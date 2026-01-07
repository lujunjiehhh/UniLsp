package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapGson
import com.google.gson.JsonElement

/**
 * Parse and deserialize a JSON-RPC command's arguments into an instance of `T`.
 *
 * @param arguments The JSON element representing the command arguments; must not be `null` or `JsonNull`.
 * @param commandName The command name used in the error message when `arguments` is missing.
 * @return An instance of `T` deserialized from `arguments`.
 * @throws DapErrors.invalidArguments if `arguments` is `null` or `JsonNull` (message: "`<commandName> requires arguments`").
 */
inline fun <reified T> parseArguments(arguments: JsonElement?, commandName: String): T {
    if (arguments == null || arguments.isJsonNull) {
        throw DapErrors.invalidArguments("$commandName requires arguments")
    }

    return DapGson.instance.fromJson(arguments, T::class.java)
}