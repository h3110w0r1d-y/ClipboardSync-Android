package com.h3110w0r1d.clipboardsync.activity

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.h3110w0r1d.clipboardsync.ui.components.MainPage
import com.h3110w0r1d.clipboardsync.viewmodel.ClipboardViewModel


class MainActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        val viewModel by viewModels<ClipboardViewModel>()
        MainPage(viewModel)
    }
}