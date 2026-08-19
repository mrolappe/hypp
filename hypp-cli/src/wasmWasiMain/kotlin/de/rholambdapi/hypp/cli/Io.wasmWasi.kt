@file:OptIn(ExperimentalWasmInterop::class)

package de.rholambdapi.hypp.cli

import kotlin.wasm.ExperimentalWasmInterop
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

// Kotlin 2.4.10's wasmWasi stdlib has no public file I/O: `kotlin.wasm.wasi` is an empty marker
// package (target detection only), and the stdlib's own WASI syscalls backing e.g. `println`
// (`kotlin.io`'s `wasiRawFdWrite`) are `internal`. These are hand-written raw `@WasmImport`
// bindings against `wasi_snapshot_preview1` for exactly the subset this CLI needs (open/read/
// write/close a file), following the same shape the stdlib uses internally. See
// doc/progress/phase-18-wasm-wasi.md for what was researched and why.

private const val OFLAGS_CREAT = 1
private const val OFLAGS_TRUNC = 8
private const val RIGHTS_FD_READ = 1L shl 1
private const val RIGHTS_FD_WRITE = 1L shl 6
private const val RIGHTS_FD_FILESTAT_GET = 1L shl 21

@WasmImport("wasi_snapshot_preview1", "fd_prestat_get")
private external fun wasiFdPrestatGet(fd: Int, resultPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "path_open")
private external fun wasiPathOpen(
    dirFd: Int,
    dirFlags: Int,
    pathPtr: Int,
    pathLen: Int,
    oFlags: Int,
    rightsBase: Long,
    rightsInheriting: Long,
    fdFlags: Int,
    resultPtr: Int,
): Int

@WasmImport("wasi_snapshot_preview1", "fd_filestat_get")
private external fun wasiFdFilestatGet(fd: Int, resultPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun wasiFdRead(fd: Int, iovsPtr: Int, iovsLen: Int, resultPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_write")
private external fun wasiFdWrite(fd: Int, iovsPtr: Int, iovsLen: Int, resultPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_close")
private external fun wasiFdClose(fd: Int): Int

@WasmImport("wasi_snapshot_preview1", "proc_exit")
private external fun wasiProcExit(code: Int): Unit

@WasmImport("wasi_snapshot_preview1", "args_sizes_get")
private external fun wasiArgsSizesGet(argcPtr: Int, argvBufSizePtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "args_get")
private external fun wasiArgsGet(argvPtr: Int, argvBufPtr: Int): Int

// Confirmed empirically (see doc/progress/phase-18-wasm-wasi.md): Kotlin 2.4.10's wasmWasi entry
// point does NOT populate `fun main(args: Array<String>)` from WASI's args_get() — it is always
// empty, regardless of what a WASI host passes as `args`. So Main.kt calls this directly instead
// of trusting its `main` parameter. Drops index 0 (the WASI "program name" convention — the
// driver script passes `["hypp-cli", ...userArgs]`) to match every other Kotlin target's `main`,
// which excludes the program name.
@OptIn(UnsafeWasmMemoryApi::class)
fun wasiCliArgs(): List<String> = withScopedMemoryAllocator { allocator ->
    val argcPtr = allocator.allocate(4)
    val bufSizePtr = allocator.allocate(4)
    val sizesErrno = wasiArgsSizesGet(argcPtr.address.toInt(), bufSizePtr.address.toInt())
    if (sizesErrno != 0) throw WasiIoException("args_sizes_get", "", sizesErrno)
    val argc = argcPtr.loadInt()
    if (argc == 0) return@withScopedMemoryAllocator emptyList()
    val bufSize = bufSizePtr.loadInt()

    val argvPtr = allocator.allocate(argc * 4)
    val argvBufPtr = allocator.allocate(bufSize.coerceAtLeast(1))
    val getErrno = wasiArgsGet(argvPtr.address.toInt(), argvBufPtr.address.toInt())
    if (getErrno != 0) throw WasiIoException("args_get", "", getErrno)

    val args = (0 until argc).map { i ->
        val entryAddr = (argvPtr + i * 4).loadInt().toUInt()
        var len = 0
        while ((Pointer(entryAddr) + len).loadByte().toInt() != 0) len++
        ByteArray(len) { j -> (Pointer(entryAddr) + j).loadByte() }.decodeToString()
    }
    args.drop(1)
}

private const val STDERR_FD = 2

// `kotlin.io`'s `println`/`print` are public on wasmWasi but only ever write to stdout (their
// stderr path, `printError`, is `internal` — see the file header note). Main.kt needs stderr for
// CommandResult.stderr, so this reuses the same fd_write binding directly against fd 2.
@OptIn(UnsafeWasmMemoryApi::class)
fun printErrorLine(message: String) = withScopedMemoryAllocator { allocator ->
    val bytes = (message + "\n").encodeToByteArray()
    val bufPtr = allocator.putBytes(bytes)
    val iovsPtr = allocator.allocate(8)
    (iovsPtr + 0).storeInt(bufPtr.address.toInt())
    (iovsPtr + 4).storeInt(bytes.size)
    val resultPtr = allocator.allocate(4)
    wasiFdWrite(STDERR_FD, iovsPtr.address.toInt(), 1, resultPtr.address.toInt())
    Unit
}

// `proc_exit` terminates the wasm instance and never returns; wrapped as `Nothing` purely so it
// can be used in expression position (e.g. `... ?: wasiExit(1)`) the way `exitProcess` is on JVM.
fun wasiExit(code: Int): Nothing {
    wasiProcExit(code)
    error("unreachable: proc_exit($code) returned")
}

private class WasiIoException(op: String, path: String, errno: Int) :
    RuntimeException("WASI $op failed for '$path' with errno $errno")

@OptIn(UnsafeWasmMemoryApi::class)
private fun MemoryAllocator.putBytes(bytes: ByteArray): Pointer {
    val ptr = allocate(bytes.size.coerceAtLeast(1))
    var cur = ptr
    for (b in bytes) {
        cur.storeByte(b)
        cur += 1
    }
    return ptr
}

// The single directory this CLI's Node driver preopens (see src/wasmWasiTest/js/cliRunner.mjs /
// the wasmWasiSmokeTest task) is the process's cwd, mapped to the WASI guest path ".". Preopened
// file descriptors are numbered right after stdio (0/1/2), but the exact number isn't a stable
// contract of any WASI host, so it's discovered rather than assumed to be fd 3.
@OptIn(UnsafeWasmMemoryApi::class)
private fun findPreopenedDirFd(): Int = withScopedMemoryAllocator { allocator ->
    val resultPtr = allocator.allocate(8)
    var fd = 3
    while (fd < 64) {
        if (wasiFdPrestatGet(fd, resultPtr.address.toInt()) == 0) return@withScopedMemoryAllocator fd
        fd++
    }
    error("wasmWasi: no preopened directory found (Node driver must preopen the cwd)")
}

// Each openXxx()/body pair uses its own top-level `withScopedMemoryAllocator` call rather than
// nesting one inside another — keeps each scratch-memory scope self-contained instead of relying
// on unspecified nested-scope behavior.
@OptIn(UnsafeWasmMemoryApi::class)
private fun openFile(path: String, oFlags: Int, rights: Long): Int {
    val dirFd = findPreopenedDirFd()
    return withScopedMemoryAllocator { allocator ->
        val pathBytes = path.encodeToByteArray()
        val pathPtr = allocator.putBytes(pathBytes)
        val resultPtr = allocator.allocate(4)
        val errno = wasiPathOpen(
            dirFd = dirFd,
            dirFlags = 0,
            pathPtr = pathPtr.address.toInt(),
            pathLen = pathBytes.size,
            oFlags = oFlags,
            rightsBase = rights,
            rightsInheriting = 0,
            fdFlags = 0,
            resultPtr = resultPtr.address.toInt(),
        )
        if (errno != 0) throw WasiIoException("path_open", path, errno)
        resultPtr.loadInt()
    }
}

private inline fun <T> withOpenFile(path: String, oFlags: Int, rights: Long, body: (fd: Int) -> T): T {
    val fd = openFile(path, oFlags, rights)
    try {
        return body(fd)
    } finally {
        wasiFdClose(fd)
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
actual fun readBytes(path: String): ByteArray =
    withOpenFile(path, oFlags = 0, rights = RIGHTS_FD_READ or RIGHTS_FD_FILESTAT_GET) { fd ->
    withScopedMemoryAllocator { allocator ->
        val statPtr = allocator.allocate(64)
        val statErrno = wasiFdFilestatGet(fd, statPtr.address.toInt())
        if (statErrno != 0) throw WasiIoException("fd_filestat_get", path, statErrno)
        val size = (statPtr + 32).loadLong().toInt()

        val bytes = ByteArray(size)
        var readSoFar = 0
        while (readSoFar < size) {
            val bufPtr = allocator.allocate(size - readSoFar)
            val iovsPtr = allocator.allocate(8)
            (iovsPtr + 0).storeInt(bufPtr.address.toInt())
            (iovsPtr + 4).storeInt(size - readSoFar)
            val resultPtr = allocator.allocate(4)
            val errno = wasiFdRead(fd, iovsPtr.address.toInt(), 1, resultPtr.address.toInt())
            if (errno != 0) throw WasiIoException("fd_read", path, errno)
            val n = resultPtr.loadInt()
            if (n == 0) break
            for (i in 0 until n) bytes[readSoFar + i] = (bufPtr + i).loadByte()
            readSoFar += n
        }
        bytes
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
actual fun writeBytes(path: String, bytes: ByteArray) {
    withOpenFile(path, oFlags = OFLAGS_CREAT or OFLAGS_TRUNC, rights = RIGHTS_FD_WRITE) { fd ->
        withScopedMemoryAllocator { allocator ->
            var written = 0
            while (written < bytes.size) {
                val chunk = bytes.copyOfRange(written, bytes.size)
                val bufPtr = allocator.putBytes(chunk)
                val iovsPtr = allocator.allocate(8)
                (iovsPtr + 0).storeInt(bufPtr.address.toInt())
                (iovsPtr + 4).storeInt(chunk.size)
                val resultPtr = allocator.allocate(4)
                val errno = wasiFdWrite(fd, iovsPtr.address.toInt(), 1, resultPtr.address.toInt())
                if (errno != 0) throw WasiIoException("fd_write", path, errno)
                val n = resultPtr.loadInt()
                if (n == 0) error("wasmWasi: fd_write wrote 0 bytes for '$path'")
                written += n
            }
        }
    }
}
