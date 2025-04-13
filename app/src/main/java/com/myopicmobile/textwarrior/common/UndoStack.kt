/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common

import java.util.LinkedList

/**
 * Implements undo/redo for insertion and deletion events of TextBuffer
 *
 * This class is tightly coupled to the implementation of TextBuffer, in
 * particular the inner workings of the gap data structure to optimize
 * undo/redo efficiency
 *
 * When text is inserted/deleted...
 * 1. Before text is inserted/deleted, TextBuffer calls captureInsert()/captureDelete()
 * 2. If the insertion/deletion is a continuation of the previous edit,
 * the incoming edit is merged with the top entry of the undo stack.
 * For 2 edits to be considered continuous, they must be the same type,
 * (insert or delete), occur within a pre-defined time interval of MERGE_TIME,
 * and the later edit must start off where the caret would have been after
 * the earlier edit.
 * 3. If the incoming edit is not continuous with the previous one, a new entry
 * for it is pushed on the stack
 *
 * Batch mode:
 * A client application can specify consecutive insert/delete operations to
 * undo/redo as a group. Edits made between a call to beginBatchEdit()
 * and a closing endBatchEdit() call are grouped as a unit.
 *
 * Undo/redo:
 * Undo/redo commands merely move the stack pointer and do not delete or insert
 * entries. Only when a new edit is made will the entries after the stack
 * pointer be deleted.
 *
 * Optimizaton notes:
 * Edited characters are copied lazily. When a new entry is pushed on the undo
 * stack, only the starting position and length of the inserted/deleted segment
 * is recorded. When another entry is pushed or when the entry is first undone,
 * the affected characters are then copied over. This optimization exploits the
 * non-destructive nature of continuous edits in TextBuffer -- deleted characters
 * can be retrieved from the gap and inserted characters are trivially available.
 * For undo/redo of the topmost entry, only the gap boundaries of TextBuffer
 * need to be moved.
 */
class UndoStack(private val _buf: TextBuffer) {
    private val _stack = LinkedList<Command>()
    var isBatchEdit: Boolean = false
        private set

    /** for grouping batch operations  */
    private var _groupId = 0

    /** where new entries should go  */
    private var _top = 0

    /** timestamp for the previous edit operation  */
    var _lastEditTime: Long = -1

    /**
     * Undo the previous insert/delete operation
     *
     * @return The suggested position of the caret after the undo, or -1 if
     * there is nothing to undo
     */
    fun undo(): Int {
        if (canUndo()) {
            var lastUndone = _stack.get(_top - 1)
            val group = lastUndone._group
            do {
                val c = _stack.get(_top - 1)
                if (c._group != group) {
                    break
                }

                lastUndone = c
                c.undo()
                --_top
            } while (canUndo())

            return lastUndone.findUndoPosition()
        }

        return -1
    }

    /**
     * Redo the previous insert/delete operation
     *
     * @return The suggested position of the caret after the redo, or -1 if
     * there is nothing to redo
     */
    fun redo(): Int {
        if (canRedo()) {
            var lastRedone = _stack.get(_top)
            val group = lastRedone._group
            do {
                val c = _stack.get(_top)
                if (c._group != group) {
                    break
                }

                lastRedone = c
                c.redo()
                ++_top
            } while (canRedo())

            return lastRedone.findRedoPosition()
        }

        return -1
    }

    //TODO extract common parts of captureInsert and captureDelete
    /**
     * Records an insert operation. Should be called before the insertion is
     * actually done.
     */
    fun captureInsert(start: Int, length: Int, time: Long) {
        var mergeSuccess = false

        if (canUndo()) {
            val c = _stack.get(_top - 1)

            if (c is InsertCommand
                && c.merge(start, length, time)
            ) {
                mergeSuccess = true
            } else {
                c.recordData()
            }
        }

        if (!mergeSuccess) {
            push(InsertCommand(start, length, _groupId))

            if (!this.isBatchEdit) {
                _groupId++
            }
        }

        _lastEditTime = time
    }

    /**
     * Records a delete operation. Should be called before the deletion is
     * actually done.
     */
    fun captureDelete(start: Int, length: Int, time: Long) {
        var mergeSuccess = false

        if (canUndo()) {
            val c = _stack.get(_top - 1)

            if (c is DeleteCommand
                && c.merge(start, length, time)
            ) {
                mergeSuccess = true
            } else {
                c.recordData()
            }
        }

        if (!mergeSuccess) {
            push(DeleteCommand(start, length, _groupId))

            if (!this.isBatchEdit) {
                _groupId++
            }
        }

        _lastEditTime = time
    }

    private fun push(c: Command?) {
        trimStack()
        ++_top
        _stack.add(c!!)
    }

    private fun trimStack() {
        while (_stack.size > _top) {
            _stack.removeLast()
        }
    }

    fun canUndo(): Boolean {
        return _top > 0
    }

    fun canRedo(): Boolean {
        return _top < _stack.size
    }

    fun beginBatchEdit() {
        this.isBatchEdit = true
    }

    fun endBatchEdit() {
        this.isBatchEdit = false
        _groupId++
    }


    private abstract inner class Command {
        /** Start position of the edit  */
        var _start: Int = 0

        /** Length of the affected segment  */
        var _length: Int = 0

        /** Contents of the affected segment  */
        var _data: String? = null

        /** Group ID. Commands of the same group are undone/redone as a unit  */
        var _group: Int = 0

        abstract fun undo()
        abstract fun redo()

        /** Populates _data with the affected text  */
        abstract fun recordData()
        abstract fun findUndoPosition(): Int
        abstract fun findRedoPosition(): Int

        /**
         * Attempts to merge in an edit. This will only be successful if the new
         * edit is continuous. See [UndoStack] for the requirements
         * of a continuous edit.
         *
         * @param start Start position of the new edit
         * @param length Length of the newly edited segment
         * @param time Timestamp when the new edit was made. There are no
         * restrictions  on the units used, as long as it is consistently used
         * in the whole program
         *
         * @return Whether the merge was successful
         */
        abstract fun merge(start: Int, length: Int, time: Long): Boolean


        val MERGE_TIME: Long = 1000000000 //750ms in nanoseconds

    }

    private inner class InsertCommand(start: Int, length: Int, groupNumber: Int) : Command() {
        /**
         * Corresponds to an insertion of text of size length just before
         * start position.
         */
        init {
            _start = start
            _length = length
            _group = groupNumber
        }

        override fun merge(newStart: Int, length: Int, time: Long): Boolean {
            if (_lastEditTime < 0) {
                return false
            }

            if ((time - _lastEditTime) < MERGE_TIME
                && newStart == _start + _length
            ) {
                _length += length
                trimStack()
                return true
            }

            return false
        }

        override fun recordData() {
            //TODO handle memory allocation failure
            _data = _buf.subSequence(_start, _length).toString()
        }

        override fun undo() {
            if (_data == null) {
                recordData()
                _buf.shiftGapStart(-_length)
            } else {
                //dummy timestamp of 0
                _buf.delete(_start, _length, 0, false)
            }
        }

        override fun redo() {
            //dummy timestamp of 0
            _buf.insert(_data!!.toCharArray(), _start, 0, false)
        }

        override fun findRedoPosition(): Int {
            return _start + _length
        }

        override fun findUndoPosition(): Int {
            return _start
        }
    }


    private inner class DeleteCommand(start: Int, length: Int, seqNumber: Int) : Command() {
        /**
         * Corresponds to an deletion of text of size length starting from
         * start position, inclusive.
         */
        init {
            _start = start
            _length = length
            _group = seqNumber
        }

        override fun merge(newStart: Int, length: Int, time: Long): Boolean {
            if (_lastEditTime < 0) {
                return false
            }

            if ((time - _lastEditTime) < MERGE_TIME
                && newStart == _start - _length - length + 1
            ) {
                _start = newStart
                _length += length
                trimStack()
                return true
            }

            return false
        }

        override fun recordData() {
            //TODO handle memory allocation failure
            _data = String(_buf.gapSubSequence(_length))
        }

        override fun undo() {
            if (_data == null) {
                recordData()
                _buf.shiftGapStart(_length)
            } else {
                //dummy timestamp of 0
                _buf.insert(_data!!.toCharArray(), _start, 0, false)
            }
        }

        override fun redo() {
            //dummy timestamp of 0
            _buf.delete(_start, _length, 0, false)
        }

        override fun findRedoPosition(): Int {
            return _start
        }

        override fun findUndoPosition(): Int {
            return _start + _length
        }
    } // end inner class
}
