package tice.utility

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun <T : Any> T.getLogger(): Lazy<Logger> = lazy { LoggerFactory.getLogger(this::class.java) }
