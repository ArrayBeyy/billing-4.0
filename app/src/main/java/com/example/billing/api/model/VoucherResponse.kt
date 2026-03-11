package com.example.billing.api.model

import com.google.gson.annotations.SerializedName

data class VoucherResponse(
    val message: String,
    val voucher: Voucher
)

data class Voucher(
    val code: String,
    val customer_name: String,
    val duration: Int,
    val id: Int,

    @SerializedName("start_time")
    val start_time: String? = null
)