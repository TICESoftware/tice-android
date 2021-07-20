package tice.helper

import kotlinx.coroutines.Job

suspend fun Job.joinAllChildren() = children.forEach { it.join() }