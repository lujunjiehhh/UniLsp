package com.frenchef.intellijlsp.dap.handlers

import com.frenchef.intellijlsp.dap.DapErrors
import com.frenchef.intellijlsp.dap.DapGson
import com.google.gson.JsonElement

inline fun <reified T> parseArguments(arguments: JsonElement?, commandName: String): T {
    if (arguments == null || arguments.isJsonNull) {
        throw DapErrors.invalidArguments("$commandName requires arguments")
    }

    return DapGson.instance.fromJson(arguments, T::class.java)
}
