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
            .registerTypeAdapter(StoppedReason::class.java, StoppedReasonSerializer())
            .registerTypeAdapter(StoppedReason::class.java, StoppedReasonDeserializer())
            .registerTypeAdapter(ThreadEventReason::class.java, EnumLowercaseSerializer<ThreadEventReason>())
            .registerTypeAdapter(OutputCategory::class.java, EnumLowercaseSerializer<OutputCategory>())
            .registerTypeAdapter(BreakpointEventReason::class.java, EnumLowercaseSerializer<BreakpointEventReason>())
            .registerTypeAdapter(ModuleEventReason::class.java, EnumLowercaseSerializer<ModuleEventReason>())
            .registerTypeAdapter(LoadedSourceEventReason::class.java, EnumLowercaseSerializer<LoadedSourceEventReason>())
            .registerTypeAdapter(ProcessStartMethod::class.java, EnumLowercaseSerializer<ProcessStartMethod>())
            .registerTypeAdapter(InvalidatedArea::class.java, EnumLowercaseSerializer<InvalidatedArea>())
            .registerTypeAdapter(SourcePresentationHint::class.java, EnumLowercaseSerializer<SourcePresentationHint>())
            .registerTypeAdapter(StackFramePresentationHint::class.java, EnumLowercaseSerializer<StackFramePresentationHint>())
            .registerTypeAdapter(BreakpointReason::class.java, EnumLowercaseSerializer<BreakpointReason>())
            .registerTypeAdapter(ExceptionBreakMode::class.java, EnumLowercaseSerializer<ExceptionBreakMode>())
            .registerTypeAdapter(SteppingGranularity::class.java, EnumLowercaseSerializer<SteppingGranularity>())
            .registerTypeAdapter(EvaluateContext::class.java, EnumLowercaseSerializer<EvaluateContext>())
            .registerTypeAdapter(PathFormat::class.java, EnumLowercaseSerializer<PathFormat>())
            .registerTypeAdapter(VariablesFilter::class.java, EnumLowercaseSerializer<VariablesFilter>())
            .registerTypeAdapter(ChecksumAlgorithm::class.java, ChecksumAlgorithmSerializer())
            .registerTypeAdapter(OutputGroup::class.java, OutputGroupSerializer())
            .serializeNulls()
            .create()
    }
    
    /**
     * Custom serializer for StoppedReason enum to handle space-separated values.
     */
    private class StoppedReasonSerializer : JsonSerializer<StoppedReason> {
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
    }
    
    private class StoppedReasonDeserializer : JsonDeserializer<StoppedReason> {
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
     * Generic lowercase enum serializer.
     */
    private class EnumLowercaseSerializer<T : Enum<T>> : JsonSerializer<T>, JsonDeserializer<T> {
        override fun serialize(src: T?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return if (src != null) JsonPrimitive(src.name.lowercase()) else JsonNull.INSTANCE
        }
        
        @Suppress("UNCHECKED_CAST")
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): T? {
            val value = json?.asString ?: return null
            val enumClass = (typeOfT as Class<T>)
            return enumClass.enumConstants.find { it.name.lowercase() == value.lowercase() }
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
