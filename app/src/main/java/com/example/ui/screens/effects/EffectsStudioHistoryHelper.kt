package com.example.ui.screens.effects

import com.example.model.PARAM_KEY_INTENSITY
import com.example.model.ShaderEffectPass

enum class HistoryEntryType {
    PARAMETER_CHANGE,
    LAYER_ORDER_CHANGE,
    EFFECT_STACK_CHANGE,
    KEYFRAME_CHANGE
}

/**
 * Immutable history checkpoint capturing Effect Studio shader parameters,
 * active selection, effect pass chain order, and project layer order.
 */
data class EffectsStudioHistorySnapshot(
    val passes: List<ShaderEffectPass>,
    val selectedPassIndex: Int,
    val layerOrderIds: List<String> = emptyList(),
    val selectedKeyframePropertyKey: String = PARAM_KEY_INTENSITY,
    val entryType: HistoryEntryType = HistoryEntryType.EFFECT_STACK_CHANGE,
    val description: String = ""
) {
    fun hasSameContentAs(other: EffectsStudioHistorySnapshot): Boolean {
        return passes == other.passes &&
            selectedPassIndex == other.selectedPassIndex &&
            layerOrderIds == other.layerOrderIds
    }
}

/**
 * State-management helper managing bounded Undo/Redo stacks, coalesced continuous
 * parameter slider edits, and multi-step drag transactions (effect pass & layer reordering)
 * for the Effect Studio.
 */
class EffectsStudioHistoryHelper(
    private val maxHistorySize: Int = 50,
    private val coalesceWindowMs: Long = 650L,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val undoStack = ArrayDeque<EffectsStudioHistorySnapshot>()
    private val redoStack = ArrayDeque<EffectsStudioHistorySnapshot>()

    private var activeTransactionKey: String? = null
    private var activeTransactionStartSnapshot: EffectsStudioHistorySnapshot? = null
    private var activeTransactionPushedToUndo: Boolean = false
    private var lastEventTimeMs: Long = 0L

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    val nextUndoDescription: String?
        get() = undoStack.lastOrNull()?.description?.takeIf { it.isNotBlank() }

    val nextRedoDescription: String?
        get() = redoStack.lastOrNull()?.description?.takeIf { it.isNotBlank() }

    val isTransactionActive: Boolean
        get() = activeTransactionKey != null

    /**
     * Records a discrete, atomic history step before mutating studio state.
     */
    fun recordSnapshot(snapshotBefore: EffectsStudioHistorySnapshot) {
        clearTransactionState()
        pushUndoBounded(snapshotBefore)
        redoStack.clear()
    }

    /**
     * Begins a multi-step gesture transaction (e.g. drag-and-drop reorder or keyframe drag).
     * Subsequent updates inside the gesture do not push duplicate undo entries until
     * [commitTransaction] is called.
     */
    fun beginTransaction(
        transactionKey: String,
        snapshotBefore: EffectsStudioHistorySnapshot
    ) {
        if (activeTransactionKey == transactionKey) return
        clearTransactionState()
        activeTransactionKey = transactionKey
        activeTransactionStartSnapshot = snapshotBefore
        activeTransactionPushedToUndo = true
        lastEventTimeMs = clockMs()
        pushUndoBounded(snapshotBefore)
        redoStack.clear()
    }

    /**
     * Records a continuous parameter change (e.g., slider or rotary jog wheel).
     * Repeated edits with the same [coalesceKey] inside [coalesceWindowMs]—or while an
     * explicit slider transaction is active—collapse into a single Undo step.
     */
    fun recordCoalescedChange(
        coalesceKey: String,
        snapshotBefore: EffectsStudioHistorySnapshot
    ) {
        val now = clockMs()
        val sameKey = activeTransactionKey == coalesceKey
        val withinWindow = (now - lastEventTimeMs) <= coalesceWindowMs
        if (sameKey && withinWindow) {
            lastEventTimeMs = now
            return
        }

        clearTransactionState()
        activeTransactionKey = coalesceKey
        activeTransactionStartSnapshot = snapshotBefore
        activeTransactionPushedToUndo = true
        lastEventTimeMs = now
        pushUndoBounded(snapshotBefore)
        redoStack.clear()
    }

    /**
     * Completes an active drag or slider transaction. If the resulting [currentState]
     * ended up identical to the pre-transaction checkpoint (a no-op drag back to start),
     * the empty undo step is discarded cleanly.
     */
    fun commitTransaction(currentState: EffectsStudioHistorySnapshot) {
        val startSnapshot = activeTransactionStartSnapshot
        val pushed = activeTransactionPushedToUndo
        clearTransactionState()
        if (pushed && startSnapshot != null && startSnapshot.hasSameContentAs(currentState)) {
            if (undoStack.lastOrNull() === startSnapshot) {
                undoStack.removeLast()
            }
        }
    }

    /**
     * Pops the most recent checkpoint from the Undo stack and pushes [currentState]
     * onto the Redo stack. Returns null if there is nothing to undo.
     */
    fun undo(currentState: EffectsStudioHistorySnapshot): EffectsStudioHistorySnapshot? {
        clearTransactionState()
        val target = undoStack.removeLastOrNull() ?: return null
        val redoCheckpoint = currentState.copy(
            entryType = target.entryType,
            description = target.description
        )
        pushBounded(redoStack, redoCheckpoint)
        return target
    }

    /**
     * Pops the most recent checkpoint from the Redo stack and pushes [currentState]
     * onto the Undo stack. Returns null if there is nothing to redo.
     */
    fun redo(currentState: EffectsStudioHistorySnapshot): EffectsStudioHistorySnapshot? {
        clearTransactionState()
        val target = redoStack.removeLastOrNull() ?: return null
        val undoCheckpoint = currentState.copy(
            entryType = target.entryType,
            description = target.description
        )
        pushUndoBounded(undoCheckpoint)
        return target
    }

    fun clear() {
        clearTransactionState()
        undoStack.clear()
        redoStack.clear()
    }

    private fun clearTransactionState() {
        activeTransactionKey = null
        activeTransactionStartSnapshot = null
        activeTransactionPushedToUndo = false
    }

    private fun pushUndoBounded(snapshot: EffectsStudioHistorySnapshot) {
        pushBounded(undoStack, snapshot)
    }

    private fun pushBounded(
        deque: ArrayDeque<EffectsStudioHistorySnapshot>,
        snapshot: EffectsStudioHistorySnapshot
    ) {
        deque.addLast(snapshot)
        while (deque.size > maxHistorySize) {
            deque.removeFirst()
        }
    }
}
