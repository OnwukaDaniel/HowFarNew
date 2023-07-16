package com.azur.howfar.activity

interface UnCaughtException {
    fun uncaughtException(thread: Thread, e: Throwable)
}