package com.example.carcompanion.viewmodel

import androidx.lifecycle.ViewModel
import com.example.carcompanion.auth.CustomTabsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    val customTabsHelper: CustomTabsHelper
) : ViewModel()
