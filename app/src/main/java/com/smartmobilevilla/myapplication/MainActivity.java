package com.smartmobilevilla.myapplication;

import android.os.Bundle;


import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;


import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.github.tcking.giraffecompressor.GiraffeCompressor;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;

public class MainActivity extends AppCompatActivity {

    static String st_pool_id="--"; //enter pool id
    String st_video_directory="--"; ////enter directory name
    String imgfilename= "";
    private Uri fileUri;
    String filePath;
    public static final int MEDIA_TYPE_VIDEO = 1;
    private static final int CAMERA_Camera_CAPTURE_VIDEO_REQUEST_CODE = 400;
    private static final int CAMERA_CAPTURE_VIDEO_REQUEST_CODE = 200;
    private String pathToStoredVideo;

    ProgressBar progress_bar_video;
    ImageView iv_complaint_video_capture;
    TextView tv_process_name;






    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgfilename = new SimpleDateFormat("yyyyMMddHHmmss",
                Locale.getDefault()).format(new Date());


        progress_bar_video=findViewById(R.id.progress_bar_video);
        progress_bar_video.setVisibility(View.GONE);

        tv_process_name=findViewById(R.id.tv_process_name);
        tv_process_name.setText("");
        tv_process_name.setVisibility(View.GONE);


        iv_complaint_video_capture=findViewById(R.id.iv_complaint_video_capture);
        iv_complaint_video_capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                captureImage();

            }
        });

    }


    private void captureImage() {

        final CharSequence[] options = { "Camera", "Choose from Gallery","Cancel" };
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {


                if(options[item].equals("Camera"))
                    callVideoCaptureAction();
                else if(options[item].equals("Choose from Gallery"))
                    callVideoPickAction();

                else if (options[item].equals("Cancel")) {
                    dialog.dismiss();
                }

            }
        });
        builder.show();


    }


    private void callVideoPickAction() {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_PICK,
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(Intent.createChooser(intent, "Select a Video "), CAMERA_CAPTURE_VIDEO_REQUEST_CODE);
        }
        catch (Exception e){

            Log.d("Error",e.toString());
        }

    }


    private void callVideoCaptureAction(){
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

        fileUri = getOutputMediaFileUri(MEDIA_TYPE_VIDEO);


        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(intent, CAMERA_Camera_CAPTURE_VIDEO_REQUEST_CODE);
    }



    public Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * create file with extension from clicked image
     */

    private  File getOutputMediaFile(int type) {

        /**
         *  External sdcard location
         */
        File mediaStorageDir = new File(
                Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                st_video_directory
        );

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }


        File mediaFile;
        if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator
                    + imgfilename + ".mp4");


        } else {
            return null;
        }

        return mediaFile;
    }






    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable("file_uri", fileUri);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        fileUri = savedInstanceState.getParcelable("file_uri");
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);

        if (requestCode == CAMERA_CAPTURE_VIDEO_REQUEST_CODE && resultCode==RESULT_OK) {

            if(data!=null) {
                fileUri = data.getData();

                pathToStoredVideo = getRealPathFromURIPath(fileUri, this);
                Log.d("vid", "Recorded Video Path " + pathToStoredVideo);
                String videoFileName=imgfilename+".mp4";

                if (pathToStoredVideo != null && !pathToStoredVideo.equalsIgnoreCase("")) {

                    compressVideoAndUpload(videoFileName);

                } else {
                    Toast.makeText(MainActivity.this, "Please select video file from gallery", Toast.LENGTH_LONG)
                            .show();
                }
            }
        }
        else if (requestCode == CAMERA_Camera_CAPTURE_VIDEO_REQUEST_CODE && resultCode==RESULT_OK) {

            if(data!=null) {
                fileUri = data.getData();
                filePath=fileUri.getPath();
                pathToStoredVideo=filePath;


                String videoFileName=imgfilename+".mp4";
                compressVideoAndUpload(videoFileName);

            }
        }

    }

    private void compressVideoAndUpload(String videoFileName)
    {
        final String vFileName="";
        try {

            GiraffeCompressor.init(MainActivity.this);

            tv_process_name.setText("Processing...");
            tv_process_name.setVisibility(View.VISIBLE);
            GiraffeCompressor.create()
                    .input(pathToStoredVideo) //set video to be compressed
                    .output(getFileDestinationPath()+"/"+videoFileName) //set compressed video output
                    .bitRate(2573600)
                    .resizeFactor(1.0f)
                    .ready()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Subscriber<GiraffeCompressor.Result>() {
                        @Override
                        public void onCompleted() {


                        }
                        @Override
                        public void onError(Throwable e) {

                            tv_process_name.setText("");
                            tv_process_name.setVisibility(View.GONE);

                            e.printStackTrace();

                        }

                        @Override
                        public void onNext(GiraffeCompressor.Result s) {

                            tv_process_name.setText("");
                            tv_process_name.setVisibility(View.GONE);


                            File f_upload=new File(getFileDestinationPath()+"/"+vFileName);

                            int video_size = 50;

                            // Get length of file in bytes
                            long fileSizeInBytes = f_upload.length();
                            // Convert the bytes to Kilobytes (1 KB = 1024 Bytes)
                            long fileSizeInKB = fileSizeInBytes / 1024;
                            // Convert the KB to MegaBytes (1 MB = 1024 KBytes)
                            long fileSizeInMB = fileSizeInKB / 1024;


                            progress_bar_video.setMax((int) fileSizeInBytes);

                            if (fileSizeInMB > video_size) {
                                Toast.makeText(MainActivity.this, "file max size is " + video_size + " mb", Toast.LENGTH_LONG).show();
                            } else {
                                uploadtos3(MainActivity.this,f_upload);
                            }

                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    String compressFileUpload="";

    private String getFileDestinationPath(){

        String filePathEnvironment = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getAbsolutePath();
        File directoryFolder = new File(filePathEnvironment + "/video/");

        if(!directoryFolder.exists()){
            directoryFolder.mkdir();
        }
        Log.d("File_Path", "Full path " + filePathEnvironment + "/video");
        compressFileUpload=filePathEnvironment + "/video" ;
        return compressFileUpload;
    }


    private String getRealPathFromURIPath(Uri contentURI, Activity activity) {

        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = activity.getContentResolver().query(contentURI, projection,  null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }


    public void uploadtos3 (final Context context, final File file) {

        progress_bar_video.setVisibility(View.VISIBLE);

        tv_process_name.setText("Uploading...");
        tv_process_name.setVisibility(View.VISIBLE);

        if(file !=null){
            CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                    context,
                    st_pool_id, // Identity pool ID
                    Regions.AP_SOUTH_1 // Region
            );

            AmazonS3 s3 = new AmazonS3Client(credentialsProvider, Region.getRegion(Regions.AP_SOUTH_1));

            TransferNetworkLossHandler.getInstance(context);

            TransferUtility transferUtility = new TransferUtility(s3, context);
            final TransferObserver observer = transferUtility.upload(
                    "bucket-name", //enter bucket name
                    file.getName(),
                    file,
                    CannedAccessControlList.BucketOwnerFullControl
            );
            observer.setTransferListener(new TransferListener() {
                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (state.equals(TransferState.COMPLETED)) {
                        Toast.makeText(context,"Success",Toast.LENGTH_LONG).show();

                        tv_process_name.setText("");
                        tv_process_name.setVisibility(View.GONE);
                        progress_bar_video.setVisibility(View.GONE);
                    } else if (state.equals(TransferState.FAILED)) {
                        Toast.makeText(context,"Failed to upload",Toast.LENGTH_LONG).show();

                        tv_process_name.setText("");
                        tv_process_name.setVisibility(View.GONE);
                        progress_bar_video.setVisibility(View.GONE);
                    }

                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {


                    progress_bar_video.setMax((int) bytesTotal);
                    progress_bar_video.setProgress((int) bytesCurrent);
                    //Log.d("progress", String.valueOf((bytesCurrent/bytesTotal)*100)+'%');
                    Log.d("progress", ((bytesTotal - bytesCurrent) / 2048) / 1024 +"%");

                }

                @Override
                public void onError(int id, Exception ex) {
                    tv_process_name.setText("");
                    tv_process_name.setVisibility(View.GONE);
                    Log.d("aws",ex.getMessage());
                }
            });
        }
    }

}
