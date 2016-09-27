package com.recorder.video.me.thevideorecorderas.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.recorder.video.me.thevideorecorderas.CameraHelper;
import com.recorder.video.me.thevideorecorderas.CameraWrapper;
import com.recorder.video.me.thevideorecorderas.R;
import com.recorder.video.me.thevideorecorderas.Util;
import com.recorder.video.me.thevideorecorderas.core.BaseRecorder.OnRecordingListener;
import com.recorder.video.me.thevideorecorderas.core.DefaultVideoRecorder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class VideoRecordingActivity extends Activity implements SurfaceHolder.Callback{

	//SURFACE WHERE YOU PREVIEW WHAT YOU RECORD

	private SurfaceView mSurfaceView;
	private SurfaceHolder mSurfaceHolder;

	private CameraWrapper mCameraPair;
	private DefaultVideoRecorder mRecorder;
	
	private ImageView btStartStop, ivSwitchCamera;
	private TextView tvDuration;
	private long seconds = 0;

    public static Activity fa;

    @SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.screen_recording_video);

		fa = this;

		//DETERMINE IF THE CAMERA IS AVAILABLE

		if(!getCameraObject(true, CameraInfo.CAMERA_FACING_BACK)){
			Toast.makeText(getBaseContext(),
					R.string.not_support_camera, Toast.LENGTH_LONG).show();
            finish();
			return;
		}
		
		mSurfaceView = (SurfaceView) findViewById(R.id.surface_camera);
		mSurfaceHolder = mSurfaceView.getHolder();
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        btStartStop = (ImageView) findViewById(R.id.bt_start_stop);
        btStartStop.setOnClickListener(onClick);
		btStartStop.setVisibility(View.INVISIBLE);
		btStartStop.setTag(new String("start"));
		
		ivSwitchCamera = (ImageView)findViewById(R.id.switch_camera);
		ivSwitchCamera.setVisibility(Camera.getNumberOfCameras() > 1 ? View.VISIBLE
                : View.GONE);
		ivSwitchCamera.setOnClickListener(onClick);
		
		tvDuration = (TextView)findViewById(R.id.duration);


		//START PREVIEW

		initRecorder();

	}
	
	private void initRecorder(){
		if(mRecorder != null){
			mRecorder.release();
		}
		mRecorder = new DefaultVideoRecorder();
		mRecorder.bindCamera(mCameraPair);
		mRecorder.setOnRecordingListener(new OnRecordingListener() {

			@Override
			public void onPrepared() {
				runOnUiThread(new Runnable() {
					public void run() {
						btStartStop.setVisibility(View.VISIBLE);
						btStartStop.setClickable(true);
						setUIState(false);
					}
				});
			}

			@Override
			public void onStart() {
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						setUIState(true);
					}
				});
			}

			@Override
			public void onError(final String error) {
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						try {
							int errorCode = Integer.valueOf(error);
							if (errorCode == -1007) {
								Toast.makeText(getApplicationContext(),
										R.string.stop_recording_video_err, Toast.LENGTH_SHORT).show();
							}
							setUIState(false);
							return;
						} catch (NumberFormatException ignored) {
						}
					}
				});
			}

			@Override
			public void onFailure() {
				onError(null);
			}

			@Override
			public void onSuccess() {
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						setUIState(false);
					}
				});
			}

			@Override
			public void onCancel() {
				onFailure();
			}
		});	
	}
	
	private void prepareRecorder(){
		btStartStop.setClickable(false);
		
		new Thread(){
			public void run() {

                try {
                    mRecorder.prepare();
                }catch (RuntimeException e)
                {e.printStackTrace();}
			};
		}.start();
	}

    //STOP/START RECORDING

	private void setUIState(boolean recording){
		if(! recording){
			btStartStop.setImageResource(R.drawable.video_record);
			btStartStop.setTag(new String("start"));
			seconds = 0;
			mUIHandler.removeCallbacks(mUIRunnable);
			tvDuration.setVisibility(View.GONE);
			ivSwitchCamera.setVisibility(View.VISIBLE);
		}else{
			btStartStop.setImageResource(R.drawable.video_stop);
			btStartStop.setTag(new String("stop"));
			seconds = 0;
			mUIHandler.postDelayed(mUIRunnable, 0);
			tvDuration.setVisibility(View.VISIBLE);
			ivSwitchCamera.setVisibility(View.INVISIBLE);
		}
	}

    //SEE IF THE RECORDING GOT TO THE UPPER LIMIT

	private Handler mUIHandler = new Handler();
	private Runnable mUIRunnable = new Runnable() {

		@Override
		public void run() {
			tvDuration.setText(Util.formatDuration(seconds++));
			if(mRecorder != null && seconds >= mRecorder.getMaxDurationInSeconds() &&
					mRecorder.reachRecorderMaxDuration()){
				mRecorder.stopRecording(false);

			}else mUIHandler.postDelayed(this, 1000);
		}
	};


	private void switchCamera() {
		//Close current camera firstly
		Camera mCamera = mCameraPair.getCamera();
		mCamera.lock();
		mCamera.stopPreview();
		mCamera.release();
		
		int camId = mCameraPair.getCameraInfo().facing;
		int toggleCamId = (camId == CameraInfo.CAMERA_FACING_BACK ? 
				CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK);
		if(! getCameraObject(false, toggleCamId)){
			return;
		}
		
		initRecorder();
		startCameraPreview();
	}
	
	public View.OnClickListener onClick = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            //START/STOP CAMERA WITH BUTTON

            if (v.getId() == R.id.bt_start_stop) {
                String tag = (String) v.getTag();

                //IF RECORDING

                if (mRecorder.isRecording() && tag.toString().equals("stop")) {
                    {
                        mRecorder.stopRecording(false);

                        //REFRESH GALLERY SO THAT YOU CAN SEE THE VIDEO

                        File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "video" + ".mp4");

                        Uri uri = Uri.fromFile(f);
                        Intent scanFileIntent = new Intent(
                                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
                        sendBroadcast(scanFileIntent);

                        //SWITCH CAMERA IF NECESSARY

                        int camId = mCameraPair.getCameraInfo().facing;
                        if (camId == CameraInfo.CAMERA_FACING_BACK) {
                           switchCamera();
                        }


                        //MAKE SURE ALL THE RESOURCES ARE FREED BEFORE YOU START THE PIC ACTIVITY

                        try {
                            if (mRecorder.isRecording()) {
                                recordedVideoPath = mRecorder.getOutputFile().getAbsolutePath();
                                mRecorder.stopRecording(true);
                            }
                            if (mRecorder != null) {
                                mRecorder.release();
                                mRecorder = null;
                            }
                        }catch (Exception e)
                        {e.printStackTrace();}

                        if(mCameraPair != null){

                            mCameraPair.getCamera().lock();
                            mCameraPair.getCamera().stopPreview();
                            mCameraPair.release();
                            mCameraPair = null;

                        }

                            Intent i = new Intent(VideoRecordingActivity.this, TakePhoto.class);
                            finish();
                            startActivity(i);


                    }

                } else if (tag.toString().equals("start")) {
                    mRecorder.startRecording();
                }
            } else if (v.getId() == R.id.switch_camera) {
                switchCamera();
            }
            }

    };
	
	
	protected void onStart() {
		super.onStart();
		
		mSurfaceHolder.addCallback(this);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
		int cameraId;
		if(mCameraPair != null){
			cameraId = mCameraPair.getCameraInfo().facing;
		}else cameraId = CameraInfo.CAMERA_FACING_BACK;
		
		if(!getCameraObject(false, cameraId)){
			Toast.makeText(getBaseContext(),
					R.string.not_support_camera, Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		
		
		initRecorder();

		if(mSurfaceView.isShown()){
			//After screen OFF >  screen ON
			startCameraPreview();
		}else{
			//After press HOME
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if(!TextUtils.isEmpty(recordedVideoPath)){

			recordedVideoPath = null;
		}
	}
	
	private static String recordedVideoPath;
	
	protected void onPause() {

        try {
            if (mRecorder.isRecording() && mRecorder != null) {
                recordedVideoPath = mRecorder.getOutputFile().getAbsolutePath();
                mRecorder.stopRecording(true);
            }
            if (mRecorder != null) {
                mRecorder.release();
            }
        }catch (Exception e)
        {e.printStackTrace();}
		super.onPause();
	};
	
	protected void onStop() {
		if(mCameraPair != null){

			mCameraPair.getCamera().lock();
            mCameraPair.getCamera().stopPreview();
			mCameraPair.release();

		}
		super.onStop();
	};

	@Override
	public void onBackPressed() {
		if(! mRecorder.isRecording()){
			super.onBackPressed();
		}
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		holder.setKeepScreenOn(true);
		startCameraPreview();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
        synchronized (mSurfaceHolder) {
            //quit to mainmenu
            ((Activity) fa).finish();
        }

    }
	
	private boolean getCameraObject(boolean first, int cameraId){
		if (first && !CameraHelper.isCameraSupported(this)) return false;
		
		mCameraPair = CameraHelper.getCamera(cameraId);
		if (mCameraPair == null && Camera.getNumberOfCameras() > 1) {
			int toggleCamId = (cameraId == CameraInfo.CAMERA_FACING_BACK ? 
					CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK);
			mCameraPair = CameraHelper.getCamera(toggleCamId);
		}
		if(mCameraPair != null){
            CameraHelper.addCameraAttributes(this, mCameraPair);
			return true;
		}
		return false;
	}

	private void startCameraPreview(){
		Camera mCamera = mCameraPair.getCamera();
		if (mCamera != null) {

			mCamera.lock();
			try {
				mCamera.setPreviewDisplay(mSurfaceHolder);
				mCamera.startPreview();
//				CameraHelper.addCameraAutoFocusFeature(mCamera);
			} catch (IOException e) {
				e.printStackTrace();
			}

			prepareRecorder();
		}
	}


}
