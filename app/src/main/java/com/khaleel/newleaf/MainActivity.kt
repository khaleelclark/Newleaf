package com.khaleel.newleaf

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.khaleel.newleaf.data.JournalRepository
import com.khaleel.newleaf.ui.NewleafApp

class MainActivity : ComponentActivity() {
    private lateinit var holder: JournalHolder
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holder = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = JournalHolder(JournalRepository(applicationContext)) as T
        })[JournalHolder::class.java]
        if(savedInstanceState==null)holder.pendingMealId.value=intent.getStringExtra("checkin_meal_id")
        setContent { val pending by holder.pendingMealId.collectAsState();NewleafApp(holder.repository,pending){holder.pendingMealId.value=null} }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        holder.pendingMealId.value=intent.getStringExtra("checkin_meal_id")
    }
}

class JournalHolder(val repository: JournalRepository) : ViewModel() {
    val pendingMealId=MutableStateFlow<String?>(null)
    override fun onCleared() { repository.close() }
}
