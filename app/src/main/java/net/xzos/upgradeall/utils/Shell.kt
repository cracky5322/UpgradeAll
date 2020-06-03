package net.xzos.upgradeall.utils

import eu.darken.rxshell.cmd.Cmd
import eu.darken.rxshell.cmd.RxCmdShell
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import net.xzos.upgradeall.core.data_manager.utils.wait

object Shell {

    private val session = RxCmdShell.builder().build().open().blockingGet()
    private val rootSession = RxCmdShell.builder().root(true).build().open().blockingGet()

    fun runShellCommand(commands: String): Cmd.Result? {
        return runShell(commands, session)
    }

    fun runSuShellCommand(commands: String): Cmd.Result? {
        return runShell(commands, rootSession)
    }

    private fun runShell(commands: String, session: RxCmdShell.Session): Cmd.Result? {
        val mutex = Mutex(locked = true)
        var result: Cmd.Result? = null
        val disposable = Cmd.builder(commands).submit(session).subscribe { result1: Cmd.Result, _ ->
            result = result1
            mutex.unlock()
        }
        runBlocking { mutex.wait() }
        disposable.dispose()
        return result
    }
}

fun Cmd.Result.getOutputString(): String {
    val output = this.output
    var outputString = ""
    for (out in output)
        outputString += "$out\n"
    return outputString
}

fun Cmd.Result.getErrorsString(): String {
    val errors = this.errors
    var errorString = ""
    for (out in errors)
        errorString += "$out\n"
    return errorString
}
