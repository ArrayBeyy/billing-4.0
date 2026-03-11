package com.example.billing

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.billing.api.config.RetrofitClient
import com.example.billing.api.model.VoucherResponse
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TimerOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var timerText: TextView
    private lateinit var stopButton: Button

    private var timer: CountDownTimer? = null
    private var codeVoucher = ""
    private var branchId = ""

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.activity_timer_overlay_service, null)

        timerText = overlayView.findViewById(R.id.timerText)
        stopButton = overlayView.findViewById(R.id.stopButton)

        stopButton.setOnClickListener {
            stopVoucherNow()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager.addView(overlayView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.let {

            codeVoucher = it.getStringExtra("CODE_VOUCHER") ?: ""
            branchId = it.getStringExtra("BRANCH_ID") ?: ""

            val expiryTime = it.getLongExtra("EXPIRY_TIME", 0L)

            val now = System.currentTimeMillis()
            val millisLeft = expiryTime - now

            Log.d("TimerOverlayService", "millisLeft = $millisLeft")

            if (millisLeft > 0) {
                startTimer(millisLeft)
            } else {
                Toast.makeText(this, "Voucher sudah habis", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startTimer(millis: Long) {

        timer?.cancel()

        timer = object : CountDownTimer(millis, 1000) {

            override fun onTick(millisUntilFinished: Long) {

                val totalSeconds = millisUntilFinished / 1000

                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60

                timerText.text = String.format(
                    "Timer: %02d:%02d:%02d",
                    hours,
                    minutes,
                    seconds
                )
            }

            override fun onFinish() {

                timerText.text = "Selesai"
                stopVoucherNow()
            }

        }.start()
    }

    @SuppressLint("NewApi")
    private fun stopVoucherNow() {

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val current = LocalDateTime.now().format(formatter)

        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("code_voucher", codeVoucher)
            .addFormDataPart("branch_id", branchId)
            .addFormDataPart("time_stop", current)

        RetrofitClient.instance.stopVoucher(builder.build())
            .enqueue(object : Callback<VoucherResponse> {

                override fun onResponse(
                    call: Call<VoucherResponse>,
                    response: Response<VoucherResponse>
                ) {

                    if (response.isSuccessful) {

                        Log.d("STOP_VOUCHER", "success")

                    } else {

                        Log.e("STOP_VOUCHER", "API gagal")
                    }

                    goBackToMain()
                }

                override fun onFailure(call: Call<VoucherResponse>, t: Throwable) {

                    Log.e("STOP_VOUCHER", t.message ?: "error")
                    goBackToMain()
                }

            })
    }

    private fun goBackToMain() {

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        startActivity(intent)

        stopSelf()
    }

    override fun onDestroy() {

        timer?.cancel()

        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }

        super.onDestroy()
    }
}