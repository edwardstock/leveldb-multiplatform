package com.edwardstock.leveldb.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.edwardstock.leveldb.example.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(binding.root)
        super.onCreate(savedInstanceState)

        binding.save.setOnClickListener {
            lifecycleScope.launch {
                viewModel.addItemFlow.emit(binding.input.text?.toString())
                binding.input.text = null
            }
            Toast.makeText(this, "Added", Toast.LENGTH_SHORT).show()
        }

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = viewModel.adapter

        binding.toolbar.inflateMenu(R.menu.menu_main)
    }
}
