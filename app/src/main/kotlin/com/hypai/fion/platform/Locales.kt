package com.hypai.fion.platform

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import com.hypai.fion.R
import com.hypai.fion.core.Language

fun Context.language(): Language = Language.valueOf(getString(R.string.language))

fun Context.switchLanguage(language: Language) {
    getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
}
