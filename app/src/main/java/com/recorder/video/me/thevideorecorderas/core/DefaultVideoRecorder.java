package com.recorder.video.me.thevideorecorderas.core;

import java.io.File;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.recorder.video.me.thevideorecorderas.CameraWrapper;
import com.recorder.video.me.thevideorecorderas.ui.VideoRecordingActivity;

public class DefaultVideoRecorder extends BaseRecorder{

	final String TAG = DefaultVideoRecorder.class.getCanonicalName();
	
	private MediaRecorder recorder;
	private CameraWrapper mCameraPair;
	private boolean prepared = false;
	private boolean preparing = false;
	
	private int mMaxDuration = 10;
	private boolean reachMaxDuration = false;
	private boolean reachMaxFileSize = false;
	private static int sRotation = 90;
	private static Size sVideoSize;

	Camera mCamera;

    Camera.Parameters p;


    public DefaultVideoRecorder(){
		super();
	}
	
	public static void setRecorderRotation(int rotation){
		sRotation = rotation;
	}
	
	public static void setRecorderVideoSize(Size size){
		//TODO This may cause problem on some devices when recording...

//		sVideoSize = size;
	}
	
	public void setMaxDuration(int seconds){
		mMaxDuration = seconds;
	}
	
	public int getMaxDurationInSeconds(){
		return mMaxDuration;
	}
	
	public boolean reachRecorderMaxDuration(){
		return reachMaxDuration;
	}
	
	public boolean reachRecorderMaxFileSize(){
		return reachMaxFileSize;
	}
	
	public void bindCamera(CameraWrapper camera){
		mCameraPair = camera;
	}
	
	public boolean isPrepared(){
		return prepared;
	}
	
	public boolean isPreparing(){
		return preparing;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public boolean prepare() {
		if(isPreparing()) return false;
		
		preparing = true;
		recorder = null;
		if (recorder == null){
			recorder = new MediaRecorder();
			
			recorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
				
				@Override
				public void onInfo(MediaRecorder mr, int what, int extra) {
					if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED){
						reachMaxDuration = true;
					}else if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED){
						reachMaxFileSize = true;
					}
				}
			});
			
			recorder.setOnErrorListener(new OnErrorListener() {
				
				@Override
				public void onError(MediaRecorder mr, int what, int extra) {
					mRecordingListener.onError(""+extra);
					if(extra == -1007){
						prepare();
						isRecording = false;
					}
				}
			});
		}
		recorder.reset();
		
		mCamera = mCameraPair.getCamera();
		mCamera.unlock();

			recorder.setCamera(mCamera);
		
		recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		recorder.setOrientationHint(sRotation);
		int camId = mCameraPair.getCameraInfo().facing;
		CamcorderProfile profile = null;

		
		profile = CamcorderProfile.get(camId, CamcorderProfile.QUALITY_HIGH);
		if(profile != null){
			profile.fileFormat = MediaRecorder.OutputFormat.MPEG_4;
			profile.videoCodec = MediaRecorder.VideoEncoder.MPEG_4_SP;
//			profile.videoBitRate = 500000;
//			profile.videoFrameRate = 30000;
			if(sVideoSize != null){
				profile.videoFrameWidth = sVideoSize.width;
				profile.videoFrameHeight = sVideoSize.height;
			}
//			profile.audioBitRate = 128000;
//			profile.audioChannels = 2;
//			profile.audioCodec = MediaRecorder.AudioEncoder.AMR_NB;
//			profile.audioSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
			recorder.setProfile(profile);

			Log.i(TAG, "RECORDER profile: quality="+profile.quality+", vfw = "+profile.videoFrameWidth+", vfh = "+profile.videoFrameHeight);
			Log.i(TAG, "RECORDER profile audio: brate="+profile.audioBitRate+", chanel="+profile.audioChannels
					+", codec="+profile.audioCodec+", srate= "+profile.audioSampleRate); 
		}else{
			//http://developer.android.com/guide/appendix/media-formats.html
			recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			recorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);
			recorder.setVideoFrameRate(30);
			recorder.setVideoEncodingBitRate(500000);
			if(sVideoSize != null){
				recorder.setVideoSize(sVideoSize.width, sVideoSize.height);
			}
			
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			recorder.setAudioSamplingRate(
					AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC));
			recorder.setAudioEncodingBitRate(128000);
			recorder.setAudioChannels(2);
		}
		
		try{
            File o = new File(Environment.getExternalStorageDirectory() + "/VideoApp");

            if (!o.exists())
                o.mkdir();

			File f = new File(o, "video" + VIDEO_EXTENSION);


			f.createNewFile();

			recorder.setOutputFile(f.getAbsolutePath());
			recorder.setMaxDuration(mMaxDuration * 100000);
			recorder.prepare();
			prepared = true;
			
			setOutputFile(f);
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		
		preparing = false;
		
		super.mRecordingListener.onPrepared();
		return true;
	}
	
	@Override
	public void startRecording(){
		if(! isPrepared()) return;
		
		if(isRecording){
			return;
		}
		
		isRecording = true;
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				record();
			}
		}).start();
	}
	
	private void record(){
		try{
			recorder.start();
			super.mRecordingListener.onStart();
		}catch(Exception e){
			e.printStackTrace();
			super.mRecordingListener.onError(e.getMessage());
			
			stopRecording(true);
		}
	}

	@Override
	public void stopRecording(boolean isCancel){

        try {
            if (recorder != null && isRecording) {
                synchronized (recorder) {
                    recorder.stop();
                    recorder.reset();
                    isRecording = false;

                }
            }
        }catch(RuntimeException e) {e.printStackTrace();}

		if(! isCancel) mRecordingListener.onSuccess();
	}
	
	@Override
	public void release(){
		if(recorder != null){
			recorder.release();
			recorder = null;
		}
	}

}
