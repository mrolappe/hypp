package de.rholambdapi.hypp.internal

/**
 * Decoder for the LHA `-lh5-` compression method, which is what ST-Guide uses for every
 * object in a `.HYP` file's data region.
 *
 * Written clean-room from public descriptions of the format. The scheme is LZSS over an
 * 8 KiB (13-bit) sliding window with matches of 3..256 bytes, entropy-coded with two
 * Huffman trees that are re-transmitted at the start of every block:
 *
 * ```
 * block := blockSize:u16                      number of literal/length symbols in it
 *          codeLengthTree                     19 symbols, used only to code the next tree
 *          literalLengthTree                  510 symbols: 0..255 literal, 256.. match length
 *          offsetTree                         14 symbols: the bit width of the match offset
 *          blockSize × symbol
 * ```
 *
 * A literal/length symbol below 256 is a literal byte; at or above it, the match length is
 * `symbol - 256 + 3` and an offset symbol follows. An offset symbol `k` means a distance of
 * `k == 0 ? 0 : (1 shl (k - 1)) + <k - 1 more bits>`, counted back from the byte about to be
 * written, so distance 0 repeats the immediately preceding byte.
 *
 * Both trees are transmitted as code-length lists and reconstructed as canonical Huffman
 * codes (shortest codes first, ties broken by symbol index — the same convention DEFLATE
 * uses). The sliding window is *not* reset between blocks; only the trees are.
 */
internal object Lh5 {
    private const val WINDOW_BITS = 13
    private const val MIN_MATCH = 3
    private const val MAX_MATCH = 256

    /** 256 literals plus one code per match length in `3..256`. */
    private const val LITERAL_COUNT = 256 + MAX_MATCH - MIN_MATCH + 1
    private const val LITERAL_COUNT_BITS = 9

    /** 16 possible code lengths plus the three run-length escapes 0, 1, 2. */
    private const val CODE_LENGTH_COUNT = 19
    private const val CODE_LENGTH_COUNT_BITS = 5

    /** One offset code per window bit width, plus the zero-distance code. */
    private const val OFFSET_COUNT = WINDOW_BITS + 1
    private const val OFFSET_COUNT_BITS = 4

    /**
     * The code-length list for the code-length tree skips entries 3..5 with a 2-bit count
     * right after entry 2 — those three lengths are the ones an encoder most often leaves
     * unused, so the format spends 2 bits instead of 3 × 3.
     */
    private const val CODE_LENGTH_SKIP_AFTER = 3

    /**
     * Decompresses `bytes[offset until offset + length]` into exactly [uncompressedSize]
     * bytes, or returns null if the stream is malformed — a code that is not in the tree, a
     * match reaching back before the start of the window, a zero block size, or a read past
     * the end of the compressed region.
     */
    fun decompress(bytes: ByteArray, offset: Int, length: Int, uncompressedSize: Int): ByteArray? {
        if (offset < 0 || length < 0 || offset + length > bytes.size) return null
        if (uncompressedSize < 0) return null
        val out = ByteArray(uncompressedSize)
        val bits = BitReader(bytes, offset, offset + length)

        var remainingInBlock = 0
        var literalTree = Huffman.CONSTANT_ZERO
        var offsetTree = Huffman.CONSTANT_ZERO
        var pos = 0

        while (pos < uncompressedSize) {
            if (remainingInBlock == 0) {
                remainingInBlock = bits.read(16)
                // A zero block size carries no symbols, so a decoder that honoured it would
                // spin forever; treat it as end-of-stream, which is short of the expected
                // output and therefore reported as a failure below.
                if (remainingInBlock == 0) return null
                val codeLengthTree =
                    readCodeLengths(bits, CODE_LENGTH_COUNT, CODE_LENGTH_COUNT_BITS, CODE_LENGTH_SKIP_AFTER)
                        ?: return null
                literalTree = readLiteralLengths(bits, codeLengthTree) ?: return null
                offsetTree = readCodeLengths(bits, OFFSET_COUNT, OFFSET_COUNT_BITS, skipAfter = -1) ?: return null
            }
            remainingInBlock--

            val symbol = literalTree.decode(bits)
            if (symbol < 0) return null
            if (symbol < 256) {
                out[pos++] = symbol.toByte()
            } else {
                val matchLength = symbol - 256 + MIN_MATCH
                val offsetCode = offsetTree.decode(bits)
                if (offsetCode < 0) return null
                val distance = if (offsetCode == 0) 0 else (1 shl (offsetCode - 1)) + bits.read(offsetCode - 1)
                var from = pos - distance - 1
                if (from < 0) return null
                if (pos + matchLength > uncompressedSize) return null
                repeat(matchLength) { out[pos++] = out[from++] }
            }
            if (bits.overrun) return null
        }
        return out
    }

    /**
     * Reads a code-length list of at most [count] entries, each 3 bits, escaping to
     * `7 + <number of following 1 bits>` for lengths above 6. A leading count of zero means
     * the whole tree collapses to the single symbol that follows it.
     */
    private fun readCodeLengths(bits: BitReader, count: Int, countBits: Int, skipAfter: Int): Huffman? {
        val transmitted = bits.read(countBits)
        if (transmitted == 0) return Huffman.constant(bits.read(countBits))
        if (transmitted > count) return null

        val lengths = IntArray(count)
        var i = 0
        while (i < transmitted) {
            var codeLength = bits.read(3)
            if (codeLength == 7) {
                while (bits.readBit() == 1) {
                    codeLength++
                    if (codeLength > Huffman.MAX_CODE_LENGTH) return null
                }
            }
            lengths[i++] = codeLength
            if (i == skipAfter) {
                val skipped = bits.read(2)
                if (i + skipped > count) return null
                i += skipped // the array is already zero-filled
            }
            if (bits.overrun) return null
        }
        return Huffman.of(lengths)
    }

    /**
     * Reads the literal/length tree's own code lengths, themselves coded with
     * [codeLengthTree]. Symbols 0, 1 and 2 are run-length escapes for unused entries
     * (1, `4 bits + 3`, and `9 bits + 20` of them); any other symbol `s` is a code length of
     * `s - 2`.
     */
    private fun readLiteralLengths(bits: BitReader, codeLengthTree: Huffman): Huffman? {
        val transmitted = bits.read(LITERAL_COUNT_BITS)
        if (transmitted == 0) return Huffman.constant(bits.read(LITERAL_COUNT_BITS))
        if (transmitted > LITERAL_COUNT) return null

        val lengths = IntArray(LITERAL_COUNT)
        var i = 0
        while (i < transmitted) {
            val symbol = codeLengthTree.decode(bits)
            val skipped = when (symbol) {
                -1 -> return null
                0 -> 1
                1 -> bits.read(4) + 3
                2 -> bits.read(LITERAL_COUNT_BITS) + 20
                else -> 0
            }
            if (skipped > 0) {
                if (i + skipped > LITERAL_COUNT) return null
                i += skipped // the array is already zero-filled
            } else {
                lengths[i++] = symbol - 2
            }
            if (bits.overrun) return null
        }
        return Huffman.of(lengths)
    }
}

/**
 * A canonical Huffman decoder built from a list of per-symbol code lengths: codes are
 * assigned in order of increasing length, and within one length in order of increasing
 * symbol index.
 */
internal class Huffman private constructor(
    private val countPerLength: IntArray,
    private val symbols: IntArray,
    private val constant: Int,
) {
    /** The next symbol, or -1 if the bits read do not form a code in this tree. */
    fun decode(bits: BitReader): Int {
        if (constant >= 0) return constant
        var code = 0
        var firstCodeOfLength = 0
        var firstSymbolOfLength = 0
        for (codeLength in 1..MAX_CODE_LENGTH) {
            code = code or bits.readBit()
            val count = countPerLength[codeLength]
            if (code - firstCodeOfLength < count) return symbols[firstSymbolOfLength + code - firstCodeOfLength]
            firstSymbolOfLength += count
            firstCodeOfLength = (firstCodeOfLength + count) shl 1
            code = code shl 1
        }
        return -1
    }

    companion object {
        const val MAX_CODE_LENGTH = 16

        /** A tree that always yields [symbol] without consuming any bits. */
        fun constant(symbol: Int) = Huffman(IntArray(0), IntArray(0), symbol)

        val CONSTANT_ZERO = constant(0)

        fun of(lengths: IntArray): Huffman? {
            val countPerLength = IntArray(MAX_CODE_LENGTH + 1)
            for (length in lengths) {
                if (length > MAX_CODE_LENGTH) return null
                if (length > 0) countPerLength[length]++
            }
            // An encoder is free to transmit a list in which nothing is used at all; nothing
            // can then be decoded from it, but that is not by itself a malformed stream.
            if (countPerLength.all { it == 0 }) return constant(0)

            val offsets = IntArray(MAX_CODE_LENGTH + 2)
            for (length in 1..MAX_CODE_LENGTH) offsets[length + 1] = offsets[length] + countPerLength[length]
            val symbols = IntArray(offsets[MAX_CODE_LENGTH + 1])
            for (symbol in lengths.indices) {
                val length = lengths[symbol]
                if (length > 0) symbols[offsets[length]++] = symbol
            }
            // offsets[] was consumed as a cursor; rebuild it implicitly in decode() instead.
            return Huffman(countPerLength, symbols, constant = -1)
        }
    }
}
