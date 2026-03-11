package com.example.billing.repository

import com.example.billing.api.config.RetrofitClient
import com.example.billing.api.model.BranchResponse
import retrofit2.Call

class BranchRepository {

    fun getBranches(): Call<BranchResponse> {
        return RetrofitClient.instance.getBranches()
    }

}