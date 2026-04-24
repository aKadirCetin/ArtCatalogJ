package com.kadircetin.artcatalogj.View;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.kadircetin.artcatalogj.R;
import com.kadircetin.artcatalogj.databinding.ActivityArtBinding;

import java.io.ByteArrayOutputStream;

public class ArtActivity extends AppCompatActivity {
    private ActivityArtBinding binding;
    ActivityResultLauncher<Intent> activityResultLauncher;
    ActivityResultLauncher<String> permissionLauncher;
    Bitmap selectedImage;
    SQLiteDatabase database;
    String sqlCreateString = "CREATE TABLE IF NOT EXISTS arts(id INTEGER PRIMARY KEY, artName VARCHAR, painterName VARCHAR, year VARCHAR, image BLOB)";
    String sqlInsertString ="INSERT INTO arts(artName,painterName,year,image) VALUES (?,?,?,?)";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityArtBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        settings();
        registerLauncher();
        database = this.openOrCreateDatabase("Arts", MODE_PRIVATE, null);

        Intent intent = getIntent();
        String info = intent.getStringExtra("info");
        assert info != null;

        if (info.equals("new")) {
            //new art
            binding.nameText.setText("");
            binding.artistText.setText("");
            binding.yearText.setText("");
            binding.imageView.setImageResource(R.drawable.select_image);
            binding.btnSave.setVisibility(View.VISIBLE);
        } else {
            //old art
            int artId = intent.getIntExtra("artId", 1);
            binding.btnSave.setVisibility(View.INVISIBLE);

            try {
                Cursor cursor = database.rawQuery("SELECT * FROM arts WHERE id=?", new String[]{String.valueOf(artId)});
                int artNameIx = cursor.getColumnIndex("artName");
                int painterNameIx = cursor.getColumnIndex("painterName");
                int yearIx = cursor.getColumnIndex("year");
                int imageIx = cursor.getColumnIndex("image");

                while (cursor.moveToNext()) {
                    binding.nameText.setText(cursor.getString(artNameIx));
                    binding.artistText.setText(cursor.getString(painterNameIx));
                    binding.yearText.setText(cursor.getString(yearIx));
                    byte[] bytes = cursor.getBlob(imageIx);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    binding.imageView.setImageBitmap(bitmap);
                }
                cursor.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public void save(View view){
        String name = binding.nameText.getText().toString();
        String artistName = binding.artistText.getText().toString();
        String year = binding.yearText.getText().toString();
        Bitmap smallImage = makeSmallerImage(selectedImage,300);

        //Quality must be between 0 and 100
        ByteArrayOutputStream outputStream= new ByteArrayOutputStream();
        smallImage.compress(Bitmap.CompressFormat.PNG,50,outputStream);
        byte[] byteArray = outputStream.toByteArray();

        try{
            database.execSQL(sqlCreateString);
            SQLiteStatement sqLiteStatement = database.compileStatement(sqlInsertString);

            sqLiteStatement.bindString(1, name);
            sqLiteStatement.bindString(2, artistName);
            sqLiteStatement.bindString(3, year);
            sqLiteStatement.bindBlob(4, byteArray);

            sqLiteStatement.execute();
        }catch (Exception e){
            e.printStackTrace();
        }
        Intent intentToMain = new Intent(ArtActivity.this, MainActivity.class);
        intentToMain.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intentToMain);

    }
    public void settings() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    private Bitmap makeSmallerImage(Bitmap image,int maximumSize){
        int width = image.getWidth();
        int height= image.getHeight();
        float bitmapRatio = (float) width / height;

        if(bitmapRatio>1){
            //landscape image
            width = maximumSize;
            height = (int) (width/bitmapRatio);
        } else {
            //portrait image
            height = maximumSize;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image,width,height,true);
    }
    public void selectImage(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            //Android 33 or above is here, needs read_media_images
            checkBuildVersion(view, Manifest.permission.READ_MEDIA_IMAGES);
        }else{
            //Android 32 and below is here needs read_external_storage
            checkBuildVersion(view, Manifest.permission.READ_EXTERNAL_STORAGE);
        }

    }
    private void checkBuildVersion(View view, String manifestPermission) {
        if (ContextCompat.checkSelfPermission(ArtActivity.this, manifestPermission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, manifestPermission)) {
                Snackbar.make(view, "Permission needed for gallery", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Give Permission", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                //request permission
                                permissionLauncher.launch(manifestPermission);
                            }
                        }).show();
            } else {
                //request permission
                permissionLauncher.launch(manifestPermission);
            }
        } else {
            // there is permission and you can go to gallery
            Intent intentToGallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            activityResultLauncher.launch(intentToGallery);
        }
    }
    public void registerLauncher() {
        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                if (result.getResultCode() == RESULT_OK) {
                    Intent intentFromResult = result.getData();
                    if (intentFromResult != null) {
                        //You need to convert to bitmap to save database
                        Uri imageData = intentFromResult.getData();
                        try {
                            if (Build.VERSION.SDK_INT >= 28) {
                                assert imageData != null;
                                ImageDecoder.Source source = ImageDecoder.createSource(ArtActivity.this.getContentResolver(), imageData);
                                selectedImage = ImageDecoder.decodeBitmap(source);
                                binding.imageView.setImageBitmap(selectedImage);
                            } else {
                                selectedImage = MediaStore.Images.Media.getBitmap(ArtActivity.this.getContentResolver(), imageData);
                                binding.imageView.setImageBitmap(selectedImage);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
            @Override
            public void onActivityResult(Boolean permissionGranted) {
                if (permissionGranted) {
                    //Permission granted?
                    Intent intentToGallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    activityResultLauncher.launch(intentToGallery);
                } else {
                    //Permission denied?
                    Toast.makeText(ArtActivity.this, "Permission needed", Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}