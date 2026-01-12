package com.frenchef.intellijlsp.dap

import com.frenchef.intellijlsp.dap.model.*
import com.google.gson.*
import java.lang.reflect.Type

/**
 * DAP-specific Gson configuration.
 * 
 * DAP uses similar JSON format to LSP but with different message structure:
 * - Uses seq instead of id
 * - Uses type field to distinguish request/response/event
 * - Uses command/event fields for method names
 */
object DapGson {
    
    val instance: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(StoppedReason::class.java, StoppedReasonAdapter())
            .registerTypeAdapter(ChecksumAlgorithm::class.java, ChecksumAlgorithmSerializer())
            .registerTypeAdapter(OutputGroup::class.java, OutputGroupSerializer())
            .create()
    }
    
    /**
     * Custom adapter for StoppedReason enum to handle space-separated values.
     */
    private class StoppedReasonAdapter : JsonSerializer<StoppedReason>, JsonDeserializer<StoppedReason> {
        override fun serialize(src: StoppedReason?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return when (src) {
                StoppedReason.STEP -> JsonPrimitive("step")
                StoppedReason.BREAKPOINT -> JsonPrimitive("breakpoint")
                StoppedReason.EXCEPTION -> JsonPrimitive("exception")
                StoppedReason.PAUSE -> JsonPrimitive("pause")
                StoppedReason.ENTRY -> JsonPrimitive("entry")
                StoppedReason.GOTO -> JsonPrimitive("goto")
                StoppedReason.FUNCTION_BREAKPOINT -> JsonPrimitive("function breakpoint")
                StoppedReason.DATA_BREAKPOINT -> JsonPrimitive("data breakpoint")
                StoppedReason.INSTRUCTION_BREAKPOINT -> JsonPrimitive("instruction breakpoint")
                null -> JsonNull.INSTANCE
            }
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): StoppedReason? {
            val value = json?.asString ?: return null
            return when (value) {
                "step" -> StoppedReason.STEP
                "breakpoint" -> StoppedReason.BREAKPOINT
                "exception" -> StoppedReason.EXCEPTION
                "pause" -> StoppedReason.PAUSE
                "entry" -> StoppedReason.ENTRY
                "goto" -> StoppedReason.GOTO
                "function breakpoint" -> StoppedReason.FUNCTION_BREAKPOINT
                "data breakpoint" -> StoppedReason.DATA_BREAKPOINT
                "instruction breakpoint" -> StoppedReason.INSTRUCTION_BREAKPOINT
                else -> null
            }
        }
    }
    
    /**
     * Custom serializer for ChecksumAlgorithm.
     */
    private class ChecksumAlgorithmSerializer : JsonSerializer<ChecksumAlgorithm>, JsonDeserializer<ChecksumAlgorithm> {
        override fun serialize(src: ChecksumAlgorithm?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return when (src) {
                ChecksumAlgorithm.MD5 -> JsonPrimitive("MD5")
                ChecksumAlgorithm.SHA1 -> JsonPrimitive("SHA1")
                ChecksumAlgorithm.SHA256 -> JsonPrimitive("SHA256")
                ChecksumAlgorithm.TIMESTAMP -> JsonPrimitive("timestamp")
                null -> JsonNull.INSTANCE
            }
        }
        
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ChecksumAlgorithm? {
            return when (json?.asString) {
                "MD5" -> ChecksumAlgorithm.MD5
                "SHA1" -> ChecksumAlgorithm.SHA1
                "SHA256" -> ChecksumAlgorithm.SHA256
                "timestamp" -> ChecksumAlgorithm.TIMESTAMP
                else -> null
            }
        }
    }
    
    /**
     * Custom serializer for OutputGroup.
     */
    private class OutputGroupSerializer : JsonSerializer<OutputGroup>, JsonDeserializer<OutputGroup> {
        override fun serialize(src: OutputGroup?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return when (src) {
                OutputGroup.START -> JsonPrimitive("start")
                OutputGroup.START_COLLAPSED -> JsonPrimitive("startCollapsed")
                OutputGroup.END -> JsonPrimitive("end")
                null -> JsonNull.INSTANCE
            }
        }
        
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): OutputGroup? {
            return when (json?.asString) {
                "start" -> OutputGroup.START
                "startCollapsed" -> OutputGroup.START_COLLAPSED
                "end" -> OutputGroup.END
                else -> null
            }
        }
    }
}
