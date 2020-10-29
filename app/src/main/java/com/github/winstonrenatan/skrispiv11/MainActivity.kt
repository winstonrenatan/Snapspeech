package com.github.winstonrenatan.skrispiv11

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextRecognizer
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.StringBuilder
import java.util.*


class MainActivity : AppCompatActivity() {
    //pass value to be displayed
    lateinit var mResultText : EditText
    lateinit var mImagePreview : ImageView
    //identifier to detect intent called
    private val CAMERA_REQUEST_CODE = 1001
    private val STORAGE_REQUEST_CODE = 1002
    private val IMAGE_PICK_GALLERY_CODE = 1003
    private val IMAGE_PICK_CAMERA_CODE = 1004
    //permissions
    lateinit var cameraPermission:Array<String>
    lateinit var storagePermission:Array<String>
    //image location (Uniform Resource Identifier)
    lateinit var image_uri: Uri
    //dictionary word list
    lateinit var dictionary: List<String>
    //TTS
    lateinit var mTTS:TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //load the dictionary word list in an ascending alphabetical order
        var dictFile = "words.txt"
        var inputString = application.assets.open(dictFile).bufferedReader().use { it.readText() }
        dictionary = inputString.toLowerCase().split("\n")
        dictionary = dictionary.sorted()

        //pass value to be displayed
        mResultText = findViewById(R.id.recognizedTextResult)
        mImagePreview= findViewById(R.id.imagePreview)

        //camera permission
        cameraPermission = arrayOf(Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
        storagePermission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        //TTS
        mTTS = TextToSpeech(applicationContext, TextToSpeech.OnInitListener {status ->  
            if (status != TextToSpeech.ERROR) {
                //set language for Text to Speech
                mTTS.language = Locale.US
            }
        })

        //speak button
        speakButton.setOnClickListener{
            //get text from result text
            var toSpeak = recognizedTextResult.getText().toString()
            if (toSpeak == "") {
                //if there is no text in edit text
                Toast.makeText(this, "Enter Text.", Toast.LENGTH_SHORT).show()
            }
            else {
                //delete all the new line and replace with a space so that it can be read normally
                toSpeak = toSpeak.replace("\n", " ")
                //if there is a text in result text
                mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null)
            }
        }

        //stop button
        stopButton.setOnClickListener{
            if (mTTS.isSpeaking) {
                //if speaking then stop
                mTTS.stop()
            }
            else {
                //if not speaking
                Toast.makeText(this, "Not Speaking.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //change to other page or exit application
    override fun onPause() {
        if (mTTS.isSpeaking) {
            //if speaking then stop
            mTTS.stop()
        }
        super.onPause()
    }

    //action bar menu
    override fun onCreateOptionsMenu(menu: Menu):Boolean {
        //show menu
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    //action bar click (select image or help)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var id = item.getItemId()
        //select image
        if (id == R.id.chooseImageSource) {
            showImageImportDialog()
        }
        //help
        else if (id == R.id.help) {
            Toast.makeText(this, "Go To Help and About Page.", Toast.LENGTH_SHORT).show()
            val m=Intent(this,HelpActivity::class.java)
            startActivity(m)
        }
        return super.onOptionsItemSelected(item)
    }

    //select image from camera or gallery dialog box
    private fun showImageImportDialog() {
        //items being displayed in dialog
        val items = arrayOf("Camera", "Gallery")
        val builder = AlertDialog.Builder(this)
        //title for the dialog box
        builder.setTitle("Select Image")
                .setItems(items, DialogInterface.OnClickListener { dialog, which ->
                if (which == 0) {
                    //Camera option clicked
                    if (!checkCameraPermission()) {
                        //Camera permission not allowed, request it
                        requestCameraPermission();
                    }
                    else {
                        //Permission allowed, take picture
                        pickCamera()
                    }
                }
                else if (which == 1) {
                    //Gallery option clicked
                    if (!checkStoragePermission()) {
                        //Camera permission not allowed, request it
                        requestStoragePermission();
                    }
                    else {
                        //Permission allowed, open gallery
                        pickGallery()
                    }
                }
        })
        builder.create().show()
    }

    //camera is chosen to be image source
    private fun pickCamera() {
        //store picture to storage
        var values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "NewPicture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "Image to Text");
        image_uri =
            getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!;
        //call the intent for camera
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri)
        startActivityForResult(intent, IMAGE_PICK_CAMERA_CODE)
    }

    //gallery is chosen to be image source
    private fun pickGallery() {
        //Intent to pick image from Gallery
        val intent = Intent(Intent.ACTION_PICK)
        //set intent type to image
        intent.setType("image/*")
        startActivityForResult(intent, IMAGE_PICK_GALLERY_CODE)
    }
    //prompt request storage permission
    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(this, storagePermission, STORAGE_REQUEST_CODE)
    }
    //check storage permission
    private fun checkStoragePermission():Boolean {
        val result = (ContextCompat.checkSelfPermission(this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE) === (PackageManager.PERMISSION_GRANTED))
        return result
    }
    //prompt request camera permission
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, cameraPermission, CAMERA_REQUEST_CODE)
    }
    //check is camera can be used or not (both camera and storage to store image captured)
    private fun checkCameraPermission():Boolean {
        /* Saving image to external storage first before giving preview is recommended
        as it will produce higher quality pics*/
        val result = (ContextCompat.checkSelfPermission(this,
            Manifest.permission.CAMERA) === (PackageManager.PERMISSION_GRANTED))
        val result1 = (ContextCompat.checkSelfPermission(this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE) === (PackageManager.PERMISSION_GRANTED))
        return result and result1
    }

    //handle permission result
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.size > 0) {
                val cameraAccepted = (grantResults[0] === PackageManager.PERMISSION_GRANTED)
                val writeStorageAccepted = (grantResults[0] === PackageManager.PERMISSION_GRANTED)
                if (cameraAccepted && writeStorageAccepted) {
                    pickCamera()
                }
                else {
                    Toast.makeText(this, "Permission Denied.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        else if (requestCode == STORAGE_REQUEST_CODE) {
            if (grantResults.size > 0) {
                val writeStorageAccepted = (grantResults[0] === PackageManager.PERMISSION_GRANTED)
                if (writeStorageAccepted) {
                    pickGallery()
                }
                else {
                    Toast.makeText(this, "Permission Denied.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    //edit after image is captured or chosen
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == IMAGE_PICK_GALLERY_CODE && data != null) {
            //got image from gallery and crop
            CropImage.activity(data.getData())
                .setGuidelines(CropImageView.Guidelines.ON)
                .start(this)
        }
        if (requestCode == IMAGE_PICK_CAMERA_CODE) {
            //got image from camera and crop
            CropImage.activity(image_uri)
                .setGuidelines(CropImageView.Guidelines.ON)
                .start(this)
        }

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            //get result uri from image editted
            var resultUri = CropImage.getActivityResult(data).getUri()
            //start time to count execution time
            val startTime = System.currentTimeMillis()
            if (resultCode == Activity.RESULT_OK) {
                //set image to image view
                mImagePreview.setImageURI(resultUri)

                //get drawable bitmap for text recognition
                var bitmapDrawable = mImagePreview.getDrawable() as BitmapDrawable
                var bitmap = bitmapDrawable.getBitmap()
                //create a text recognizer
                var recognizer = TextRecognizer.Builder(getApplicationContext()).build()

                //check whether recognizer is working
                if(!recognizer.isOperational()) {
                    Toast.makeText(this, "Error.", Toast.LENGTH_SHORT).show()
                }
                //if recognizer is working then try to detect text
                else {
                    //hold value of temporary recognized text on process and its matching words
                    var tempRecognizedText = StringBuilder()
                    //value of the recognized text, numbers and matching,  in all degrees
                    var recognizedText = arrayOf<String>("", "", "", "")
                    //holds a pair value of words recognized and matching words
                    val pairList = ArrayList<Pair<Int, Int>>()
                    //holds largest value total of words recognized and matching words, together with its position
                    var largestValue = 0
                    var largestPosition = 0
                    //try to do rotation from 0, 90, 180, and 270 degree
                    for (x in 0..3) {
                        //rotate the current image by 90 degree
                        var newBitmap = bitmapRotation(bitmap, x)
                        //try to recognize the rotated image
                        tempRecognizedText = detectText(newBitmap, recognizer)
                        recognizedText[x] = tempRecognizedText.toString()
                        var lowerCasedText = recognizedText[x].toLowerCase()
                        //if the rotated image have a result
                        if (lowerCasedText!="") {
                            //split string to words
                            val wordsInString = lowerCasedText.split("\\s+".toRegex()).map { word ->
                                word.replace("""^[,\.]|[,\.]$""".toRegex(), "")
                            }
                            //find length of string
                            val tempNumWord = wordsInString.size
                            //store matching words in a degree
                            var tempMatch = 0
                            //check every word recognized to dictionary using binary search
                            for (i in 0 until tempNumWord) {
                                val wordLoc = binarySearch(dictionary, wordsInString[i])
                                if (wordLoc != -1) {
                                    tempMatch++
                                }
                            }
                            //add pair to current list
                            pairList.add(Pair(tempNumWord, tempMatch))
                            //check for the best result
                            if (largestValue<tempNumWord+tempMatch) {
                                largestValue=tempNumWord+tempMatch
                                largestPosition=x
                            }
                        }
                        else {
                            //filll pair with 0 and 0 if there are no results
                            pairList.add(Pair(0, 0))
                        }
                    }
                    //set text to edit text
                    mResultText.setText(recognizedText[largestPosition])
                    //end time to count execution time
                    val endTime = System.currentTimeMillis()
                    //execution time from end time and start time
                    val exeTime = endTime - startTime
                    Toast.makeText(this, "Total words: "+pairList[largestPosition].first+
                            " | Matching words: "+pairList[largestPosition].second+
                            " | Execution time: "+exeTime+"ms", Toast.LENGTH_LONG).show()
                }
            }
            else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE){
                Toast.makeText(this, "Error.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //text detector function
    private fun detectText(bitmap: Bitmap?, recognizer: TextRecognizer): StringBuilder {
        var sb = StringBuilder()
        //image data in bitmap
        var frame = Frame.Builder().setBitmap(bitmap).build()
        //recognize TextBlock (paragraph) from frame
        var items = recognizer.detect(frame)
        //get text from TextBlock until there is no text
        for (i in 0 until items.size()) {
            //find value of text in TextBlock i and add the value to the stringBuilder made
            sb.append(items.valueAt(i).getValue())
            //add an enter if it is not the last paragraph
            if (i!=items.size()-1) {
                sb.append("\n")
            }
        }
        return sb
    }

    //image rotation function
    private fun bitmapRotation(bitmap: Bitmap, multiply: Int): Bitmap? {
        //rotation degree
        val degree = multiply*90f
        //initialize matrix and rotate according degree
        val matrix = Matrix()
        matrix.setRotate(degree)
        //create new bitmap that is rotated
        val rotationResult =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true)
        return rotationResult
    }

    //binary search function
    private fun binarySearch(dict:List<String>, word:String):Int {
        var l = 0
        var r = dict.size - 1
        while (l <= r) {
            val m = (l + r) / 2
            val res = word.compareTo(dict[m])
            // Check if word is present at middle
            if (res == 0) {
                return m
            }
            // If x greater, then ignore left half
            if (res > 0) {
                l = m + 1
            }
            // If x is smaller, then ignore right half
            else {
                r = m - 1
            }
        }
        return -1
    }
}
