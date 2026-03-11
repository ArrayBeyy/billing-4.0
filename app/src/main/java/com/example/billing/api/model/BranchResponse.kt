package com.example.billing.api.model

data class BranchResponse(
    val success: Boolean,
    val data: List<Branch>
)