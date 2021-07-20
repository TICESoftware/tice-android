package tice.utility

import android.util.Base64

fun String.dataFromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
fun ByteArray.toBase64String(): String = Base64.encodeToString(this, Base64.NO_WRAP)
fun ByteArray.toBase64URLSafeString(): String = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP).trimEnd('=')
