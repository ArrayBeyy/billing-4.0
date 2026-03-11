package com.example.billing

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.text.InputFilter
import android.text.InputFilter.AllCaps
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.billing.api.config.RetrofitClient
import com.example.billing.api.model.BranchResponse
import com.example.billing.api.model.VoucherResponse
import com.google.android.material.textfield.TextInputLayout
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var etVoucher: EditText
    private lateinit var btnCheck: Button
    private lateinit var prefs: SharedPreferences
    private var voucherCode: String = ""

    private lateinit var actvCabang: AutoCompleteTextView
    private lateinit var tilCabang: TextInputLayout

    private var selectedCabang: String = ""
    private var selectedBranchId: Int = 0

    private var listCabang = mutableListOf<String>()

    private val REQUEST_CODE_SCREEN_CAPTURE = 2001
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var isRecording = false

    companion object {
        var storedResultCode: Int = -1
        var storedDataIntent: Intent? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        hideSystemUI()

        tilCabang = findViewById(R.id.tilCabang)
        actvCabang = findViewById(R.id.actvCabang)

        etVoucher = findViewById(R.id.etVoucher)
        etVoucher.filters = arrayOf<InputFilter>(AllCaps())

        btnCheck = findViewById(R.id.btnCheck)

        fetchCabang()

        // =========================
        // FIX RESTORE CABANG
        // =========================

        val savedCabang = prefs.getString("selected_cabang", "")
        val savedBranchId = prefs.getInt("selected_branch_id", 0)

        if (!savedCabang.isNullOrEmpty()) {
            actvCabang.setText(savedCabang, false)
            selectedCabang = savedCabang
            selectedBranchId = savedBranchId
        }

        // =========================
        // BUTTON CHECK VOUCHER
        // =========================

        btnCheck.setOnClickListener {

            if (selectedCabang.isEmpty() || selectedBranchId == 0) {
                tilCabang.error = "Pilih cabang terlebih dahulu!"
                actvCabang.requestFocus()
                return@setOnClickListener
            }

            voucherCode = etVoucher.text.toString().trim()

            if (voucherCode.isEmpty()) {
                Toast.makeText(this, "Kode voucher belum di-input.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendVoucherRequest(voucherCode)
        }
    }

    // =========================
    // LOAD CABANG
    // =========================

    private fun fetchCabang() {

        RetrofitClient.instance.getBranches().enqueue(object : Callback<BranchResponse> {

            override fun onResponse(call: Call<BranchResponse>, response: Response<BranchResponse>) {

                if (response.isSuccessful && response.body() != null) {

                    val branches = response.body()!!.data

                    listCabang.clear()

                    branches.forEach {
                        listCabang.add(it.name)
                    }

                    val adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        listCabang
                    )

                    actvCabang.setAdapter(adapter)

                    actvCabang.setOnItemClickListener { _, _, position, _ ->

                        selectedCabang = branches[position].name
                        selectedBranchId = branches[position].id

                        tilCabang.error = null

                        prefs.edit {
                            putString("selected_cabang", selectedCabang)
                            putInt("selected_branch_id", selectedBranchId)
                        }

                        Log.d("CABANG_SELECTED", "$selectedCabang -> $selectedBranchId")
                    }
                }
            }

            override fun onFailure(call: Call<BranchResponse>, t: Throwable) {
                Log.e("API_ERROR", "Gagal load cabang: ${t.message}")
            }
        })
    }

    // =========================
    // REQUEST VOUCHER
    // =========================

    private fun sendVoucherRequest(code: String) {

        Log.d("VOUCHER_REQUEST", "Code=$code Branch=$selectedBranchId")

        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("code_voucher", code)
            .addFormDataPart("branch_id", selectedBranchId.toString())

        RetrofitClient.instance.readVoucher(builder.build())
            .enqueue(object : Callback<VoucherResponse> {

                override fun onResponse(call: Call<VoucherResponse>, response: Response<VoucherResponse>) {

                    if (response.isSuccessful && response.body() != null) {

                        val voucher = response.body()!!.voucher

                        if (voucher != null) {

                            val durationSeconds = voucher.duration
                            val expiryTime = System.currentTimeMillis() + (durationSeconds * 1000)

                            unlockAndGoToWhatsApp(code, durationSeconds, expiryTime)

                        } else {

                            Toast.makeText(this@MainActivity, "Voucher tidak valid", Toast.LENGTH_SHORT).show()
                        }

                    } else {

                        Toast.makeText(this@MainActivity, "Voucher tidak valid", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<VoucherResponse>, t: Throwable) {

                    Log.e("API_ERROR", t.message ?: "Unknown")

                    Toast.makeText(this@MainActivity, "Gagal koneksi API", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // =========================
    // BUKA WHATSAPP ACTIVITY
    // =========================

    private fun unlockAndGoToWhatsApp(
        code: String,
        duration: Int,
        expiryTime: Long
    ) {

        val intent = Intent(this, WhatsAppActivity::class.java)

        intent.putExtra("CODE_VOUCHER", code)
        intent.putExtra("BRANCH_ID", selectedBranchId.toString())
        intent.putExtra("DURATION", duration)
        intent.putExtra("EXPIRY_TIME", expiryTime)

        startActivity(intent)
    }

    // =========================
    // HIDE SYSTEM UI
    // =========================

    private fun hideSystemUI() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(WindowInsets.Type.systemBars())

        } else {

            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }
}