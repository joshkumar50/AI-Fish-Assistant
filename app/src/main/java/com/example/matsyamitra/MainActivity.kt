package com.example.matsyamitra

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory // NEW IMPORT
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat

import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Main UI Elements
    private lateinit var fishImageView: ImageView
    private lateinit var takePhotoButton: Button
    private lateinit var selectFromGalleryButton: Button
    private lateinit var viewScheduleButton: Button

    // Result Card UI Elements
    private lateinit var resultDetailsLayout: LinearLayout
    private lateinit var resultTitleTextView: TextView
    private lateinit var resultLabelTextView: TextView
    private lateinit var resultConfidenceTextView: TextView
    private lateinit var resultHealthTextView: TextView
    private lateinit var resultRemedyTextView: TextView
    private lateinit var speakRemedyButton: ImageButton

    // AI Model and TTS Engine
    private var imageClassifier: ImageClassifier? = null
    private lateinit var labels: List<String>
    private var tts: TextToSpeech? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                processAndDisplayImage(imageBitmap)
            }
        }
    }

    private val selectFromGalleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                // Call the new, safe bitmap loading function
                val imageBitmap = uriToBitmap(imageUri)
                if (imageBitmap != null) {
                    processAndDisplayImage(imageBitmap)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        fishImageView = findViewById(R.id.fishImageView)
        takePhotoButton = findViewById(R.id.takePhotoButton)
        selectFromGalleryButton = findViewById(R.id.selectFromGalleryButton)
        viewScheduleButton = findViewById(R.id.viewScheduleButton)
        resultDetailsLayout = findViewById(R.id.resultDetailsLayout)
        resultTitleTextView = findViewById(R.id.resultTitleTextView)
        resultLabelTextView = findViewById(R.id.resultLabelTextView)
        resultConfidenceTextView = findViewById(R.id.resultConfidenceTextView)
        resultHealthTextView = findViewById(R.id.resultHealthTextView)
        resultRemedyTextView = findViewById(R.id.resultRemedyTextView)
        speakRemedyButton = findViewById(R.id.speakRemedyButton)

        // Initialize light-weight components
        loadLabels()
        tts = TextToSpeech(this, this)

        // Set up click listeners
        takePhotoButton.setOnClickListener {
            resultDetailsLayout.visibility = View.GONE
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureLauncher.launch(cameraIntent)
        }

        selectFromGalleryButton.setOnClickListener {
            resultDetailsLayout.visibility = View.GONE
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            selectFromGalleryLauncher.launch(galleryIntent)
        }

        viewScheduleButton.setOnClickListener {
            val intent = Intent(this, FeedingScheduleActivity::class.java)
            startActivity(intent)
        }

        speakRemedyButton.setOnClickListener {
            val remedyText = resultRemedyTextView.text.toString()
            if (remedyText.isNotEmpty()) {
                tts?.speak(remedyText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("te", "IN")
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language specified is not supported!")
            }
        } else {
            Log.e("TTS", "Initialization Failed!")
        }
    }

    private fun processAndDisplayImage(bitmap: Bitmap) {
        fishImageView.setImageBitmap(bitmap)
        analyzeImage(bitmap)
    }

    // COMPLETELY REWRITTEN - THIS IS THE MEMORY-SAFE WAY TO LOAD AN IMAGE
    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            // Step 1: Get the dimensions of the bitmap without loading it into memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Step 2: Calculate the inSampleSize to subsample the image
            val requiredSize = 512 // We want the image to be roughly this size
            var widthTmp = options.outWidth
            var heightTmp = options.outHeight
            var scale = 1
            while (true) {
                if (widthTmp / 2 < requiredSize || heightTmp / 2 < requiredSize) {
                    break
                }
                widthTmp /= 2
                heightTmp /= 2
                scale *= 2
            }

            // Step 3: Decode the bitmap with the calculated inSampleSize
            val newInputStream = contentResolver.openInputStream(uri)
            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            val bitmap = BitmapFactory.decodeStream(newInputStream, null, finalOptions)
            newInputStream?.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    private fun loadLabels() {
        try {
            assets.open("labels.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    labels = reader.readLines()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            resultTitleTextView.text = getString(R.string.error_loading_labels)
        }
    }

    private fun setupImageClassifier() {
        try {
            val options = ImageClassifier.ImageClassifierOptions.builder().setMaxResults(1).build()
            imageClassifier = ImageClassifier.createFromFileAndOptions(this, "model.tflite", options)
        } catch (e: IOException) {
            e.printStackTrace()
            resultTitleTextView.text = getString(R.string.error_loading_model)
        }
    }

    private fun analyzeImage(bitmap: Bitmap) {
        if (imageClassifier == null) {
            setupImageClassifier()
        }

        if (imageClassifier == null) {
            resultTitleTextView.text = getString(R.string.error_model_not_loaded)
            resultDetailsLayout.visibility = View.VISIBLE
            return
        }

        if (!::labels.isInitialized || labels.isEmpty()) {
            resultTitleTextView.text = getString(R.string.error_labels_not_loaded)
            resultDetailsLayout.visibility = View.VISIBLE
            return
        }

        resultTitleTextView.text = getString(R.string.analyzing_image)

        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results = imageClassifier?.classify(tensorImage)

        if (results != null && results.isNotEmpty()) {
            val topResultCategory = results[0].categories[0]
            val predictedIndex = topResultCategory.index
            val englishLabel = if (predictedIndex >= 0 && predictedIndex < labels.size) {
                labels[predictedIndex]
            } else {
                "Unknown"
            }
            val score = topResultCategory.score
            val confidencePercentage = String.format("%.2f%%", score * 100)

            val teluguLabel = when (englishLabel) {
                "White Spot Disease" -> "తెల్ల మచ్చల వ్యాధి"
                "Fin Rot" -> "ఫిన్ రాట్"
                "Catla" -> "కట్ల"
                "Rohu" -> "రోహు"
                "Tilapia" -> "తిలాపియా"
                else -> englishLabel
            }

            val healthStatus: String
            val suggestedRemedy: CharSequence

            when (englishLabel) {
                "White Spot Disease" -> {
                    healthStatus = "అనారోగ్యంగా ఉంది (తెల్ల మచ్చల వ్యాధి)"
                    suggestedRemedy = HtmlCompat.fromHtml(getString(R.string.remedy_white_spot_detail), HtmlCompat.FROM_HTML_MODE_LEGACY)
                }
                "Fin Rot" -> {
                    healthStatus = "అనారోగ్యంగా ఉంది (ఫిన్ రాట్)"
                    suggestedRemedy = HtmlCompat.fromHtml(getString(R.string.remedy_fin_rot_detail), HtmlCompat.FROM_HTML_MODE_LEGACY)
                }
                else -> {
                    healthStatus = "ఆరోగ్యంగా ఉంది"
                    suggestedRemedy = getString(R.string.remedy_healthy)
                }
            }

            resultTitleTextView.text = getString(R.string.analysis_result)
            resultLabelTextView.text = getString(R.string.result_label, teluguLabel)
            resultConfidenceTextView.text = getString(R.string.result_confidence, confidencePercentage)
            resultHealthTextView.text = getString(R.string.result_health, healthStatus)
            resultRemedyTextView.text = suggestedRemedy

            resultDetailsLayout.visibility = View.VISIBLE

        } else {
            resultTitleTextView.text = getString(R.string.could_not_identify)
            resultDetailsLayout.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        super.onDestroy()
        imageClassifier?.close()
    }
}