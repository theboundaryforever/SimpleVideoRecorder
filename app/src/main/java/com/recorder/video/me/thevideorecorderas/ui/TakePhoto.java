package com.recorder.video.me.thevideorecorderas.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;


import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transfermanager.TransferManager;
import com.amazonaws.mobileconnectors.s3.transfermanager.Upload;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.googlecode.mp4parser.DataSource;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import com.recorder.video.me.thevideorecorderas.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import java.io.FileNotFoundException;
public class TakePhoto extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_video_preview);


        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        //PREPARE THE ACTIVITY AND START INTENT WITH LOCATION FOR IMAGE TO BE SAVED

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {

            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(photoFile));

                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takePictureIntent, 1);
                }
            }
        }
    }

    //CREATE LOCATION FOR IMAGE

    private File createImageFile() throws IOException {
        // Create an image file name
        String imageFileName = "myimage";
        File storageDir = new File(Environment.getExternalStorageDirectory() + "/VideoApp");

        if (!storageDir.exists())
            storageDir.mkdir();

        File image = new File(storageDir, imageFileName + ".jpg");

        return image;
    }


    //FOR S3 UPLOADING

    public class TheTask extends AsyncTask<Void, Void, Void>
    {
        double dur = 0;
        ProgressDialog p;

        public TheTask (double dur)
        {
            this.dur = dur;
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected void onPostExecute(Void aVoid) {

            File f = new File(Environment.getExternalStorageDirectory() + "/VideoApp/"
                    + "video.mp4");

            System.setProperty("aws.accessKeyId", "AKIAIZL6LMMYDQOU5TYA");
            System.setProperty("aws.secretKey", "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");

            TransferManager tm = new TransferManager(new SystemPropertiesCredentialsProvider());

            Upload upload = tm.upload(
                    "video.stefan", "movie" + ".mp4", f);


            try {
                upload.waitForCompletion();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            f = new File(Environment.getExternalStorageDirectory() + "/VideoApp/"
                    + "myimage.jpg");

            upload = tm.upload("video.stefan", "myimage" + ".jpg", f);

            try {
                upload.waitForCompletion();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Intent i = new Intent(TakePhoto.this, VideoRecordingActivity.class);
            finish();
            startActivity(i);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {


                //CUT THE VIDEO TO ONE MINUTE

                File f = new File(Environment.getExternalStorageDirectory() + "/VideoApp/"
                        + "video.mp4");

                Movie movie = MovieCreator.build(f.getPath());
                // remove all tracks we will create new tracks from the old
                List<Track> tracks = movie.getTracks();
                movie.setTracks(new LinkedList<Track>());
                double startTime = dur - 60;
                double endTime = dur;
                boolean timeCorrected = false;

                for (Track track : tracks) {
                    if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                        if (timeCorrected) {

                            throw new RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.");
                        }
                        startTime = correctTimeToSyncSample(track, startTime, false);
                        endTime = correctTimeToSyncSample(track, endTime, true);
                        timeCorrected = true;
                    }
                }
                for (Track track : tracks) {
                    long currentSample = 0;
                    double currentTime = 0;
                    long startSample = -1;
                    long endSample = -1;
                    for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
                        TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
                        for (int j = 0; j < entry.getCount(); j++) {
                            // entry.getDelta() is the amount of time the current sample covers.
                            if (currentTime <= startTime) {
                                // current sample is still before the new starttime
                                startSample = currentSample;
                            }
                            if (currentTime <= endTime) {
                                // current sample is after the new start time and still before the new endtime
                                endSample = currentSample;
                            } else {
                                // current sample is after the end of the cropped video
                                break;
                            }
                            currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                            currentSample++;
                        }
                    }
                    movie.addTrack(new CroppedTrack(track, startSample, endSample));
                }

                Container out = new DefaultMp4Builder().build(movie);

                f.delete();

                //AND SAVE IT AGAIN

                FileOutputStream fos = new FileOutputStream(Environment.getExternalStorageDirectory() + "/VideoApp/"
                        + "video.mp4");
                FileChannel fc = fos.getChannel();

                out.writeContainer(fc);
                fc.close();
                fos.close();
            } catch (FileNotFoundException e) {

                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == RESULT_OK) {

            //AFTER YOU TAKE PIC

            MediaPlayer mp = MediaPlayer.create(this, Uri.parse(Environment.getExternalStorageDirectory() + "/VideoApp/"
                    + "video.mp4"));

            double duration = mp.getDuration() / 1000;
            mp.release();

            if (duration > 60)
                new TheTask(duration).execute();

            else {
                File f = new File(Environment.getExternalStorageDirectory() + "/VideoApp/"
                        + "video.mp4");

                System.setProperty("aws.accessKeyId", "AKIAIZL6LMMYDQOU5TYA");
                System.setProperty("aws.secretKey", "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");

                TransferManager tm = new TransferManager(new SystemPropertiesCredentialsProvider());

                Upload upload = tm.upload(
                        "video.stefan", "movie" + ".mp4", f);


                try {
                    upload.waitForCompletion();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                f = new File(Environment.getExternalStorageDirectory() + "/VideoApp/"
                        + "myimage.jpg");

                upload = tm.upload("video.stefan", "myimage" + ".jpg", f);

                try {
                    upload.waitForCompletion();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Intent i = new Intent(TakePhoto.this, VideoRecordingActivity.class);
                finish();
                startActivity(i);
            }

        }
    }

    //PREPARE VIDEO TO BE CUT

    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
            TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
            for (int j = 0; j < entry.getCount(); j++) {
                if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                    // samples always start with 1 but we start with zero therefore +1
                    timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
                }
                currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }
        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }
}
