package org.openmhealth.pam;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import org.joda.time.DateTime;
import org.json.JSONObject;
import org.openmhealth.utils.reminders.ReminderListActivity;
import org.openmhealth.utils.reminders.ReminderManager;

import java.io.IOException;
import java.util.Random;

import io.smalldatalab.android.pam.R;
import io.smalldatalab.omhclient.DSUClient;
import io.smalldatalab.omhclient.DSUDataPoint;
import io.smalldatalab.omhclient.DSUDataPointBuilder;


public class PAMActivity extends FragmentActivity {

    Bitmap[] images;
    int[] imageIds;
    private final Random random = new Random();

    private static String pam_photo_id;
    private static Location userLocation;

    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private int selection  = GridView.INVALID_POSITION;
    private GridView gridview;

    private DSUClient mDSUClient;
    public static final String[] IMAGE_FOLDERS = new String[]{
            "1_afraid",
            "2_tense",
            "3_excited",
            "4_delighted",
            "5_frustrated",
            "6_angry",
            "7_happy",
            "8_glad",
            "9_miserable",
            "10_sad",
            "11_calm",
            "12_satisfied",
            "13_gloomy",
            "14_tired",
            "15_sleepy",
            "16_serene"
    };

    private Animator mCurrentAnimator;
    private int mShortAnimationDuration;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTitle("Select a Photo");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDSUClient =
                new DSUClient(
                        DSUHelper.getUrl(PAMActivity.this),
                        this.getString(R.string.dsu_client_id),
                        this.getString(R.string.dsu_client_secret),
                        PAMActivity.this);
        loadImages();
        if (!mDSUClient.isSignedIn() ) { // && !Build.FINGERPRINT.contains("generic")
            Intent mIntent = new Intent(PAMActivity.this, SigninActivity.class);
            this.startActivity(mIntent);
        }

        // TODO JARED: is location used anymore, without ProbeLibrary?
        LocationManager locationManager = (LocationManager)
                PAMActivity.this.getSystemService(Context.LOCATION_SERVICE);
        Location gpsloc =
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location netloc =
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (isBetterLocation(gpsloc, netloc)) {
            userLocation = gpsloc;
        } else {
            userLocation = netloc;
        }


        gridview = (GridView) this.findViewById(R.id.pam_grid);

        Button submit = (Button) this.findViewById(R.id.reload_images);
        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadImages();
                setupPAM();
            }
        });

        // Retrieve and cache the system's default "short" animation time.
        mShortAnimationDuration = getResources().getInteger(
                android.R.integer.config_shortAnimTime);

    }
    @Override
    public void onResume(){
        super.onResume();
        loadImages();
        setupPAM();

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_sign_out:
                try {
                    mDSUClient.blockingSignOut();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                ReminderManager mReminderManager = new ReminderManager(PAMActivity.this);
                mReminderManager.removeAllReminders();
                Toast.makeText(PAMActivity.this, "Signed out.",
                        Toast.LENGTH_LONG).show();
                Intent mIntent = new Intent(PAMActivity.this, SigninActivity.class);
                PAMActivity.this.startActivity(mIntent);
                return true;
            case R.id.menu_reminder:
                final Intent intent = new Intent(PAMActivity.this, ReminderListActivity.class);
                PAMActivity.this.startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    

    private void onSubmit() {
        try {
            int idx = Integer.valueOf(pam_photo_id.split("_")[0]);
            DateTime dt = new DateTime();

            PamSchema pamSchema = new PamSchema(idx, dt);
            JSONObject body = pamSchema.toJSON();
            // attach location information is available
            if(userLocation != null) {
                JSONObject location = new JSONObject();
                location.put("latitude", userLocation.getLatitude());
                location.put("longitude", userLocation.getLongitude());
                location.put("accuracy", userLocation.getAccuracy());
                location.put("altitude", userLocation.getAltitude());
                location.put("bearing", userLocation.getBearing());
                location.put("speed", userLocation.getSpeed());
                location.put("timestamp", userLocation.getTime());
                body.put("location", location);
            }
            DSUDataPoint datapoint = new DSUDataPointBuilder()
                    .setSchemaNamespace(getString(R.string.schema_namespace))
                    .setSchemaName(getString(R.string.schema_name))
                    .setSchemaVersion(getString(R.string.schema_version))
                    .setAcquisitionModality(getString(R.string.acquisition_modality))
                    .setAcquisitionSource(getString(R.string.acquisition_source_name))
                    .setCreationDateTime(dt)
                    .setBody(body).createDSUDataPoint();
            datapoint.save();

            Toast.makeText(PAMActivity.this, "Submitted.", Toast.LENGTH_LONG).show();
            // clear selection
            pam_photo_id = null;

            // Trigger a sync to upload data now
            mDSUClient.forceSync();

        } catch (Exception e) {
            Toast.makeText(PAMActivity.this, "Submission failed. Please contact study coordinator", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Determines whether one Location reading is better than the current
     * Location fix
     *
     * @param location            The new Location that you want to evaluate
     * @param currentBestLocation The current Location fix, to which you want to
     *                            compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }
        if (location == null) {
            return false;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use
        // the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be
            // worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and
        // accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether two providers are the same
     */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    private void loadImages() {
        images = new Bitmap[IMAGE_FOLDERS.length];
        imageIds = new int[IMAGE_FOLDERS.length];

        AssetManager assets = getResources().getAssets();
        String subFolder;
        for (int i = 0; i < IMAGE_FOLDERS.length; i++) {
            subFolder = "pam_images/" + IMAGE_FOLDERS[i];
            try {
                String filename = assets.list(subFolder)[random.nextInt(3)];
                images[i] = BitmapFactory.decodeStream(assets.open(subFolder + "/" + filename));
                imageIds[i] = filename.split("_")[1].charAt(0) - '0';
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupPAM() {
        // Start PAM
        gridview.setAdapter(new BaseAdapter() {

            private final int width = PAMActivity.this.getWindowManager().getDefaultDisplay()
                    .getWidth();

            @Override
            public int getCount() {
                return images.length;
            }

            @Override
            public Object getItem(int arg0) {
                return null;
            }

            @Override
            public long getItemId(int position) {
                return 0;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ImageView imageView;
                if (null == convertView) {
                    imageView = new ImageView(PAMActivity.this);
                    imageView.setLayoutParams(new AbsListView.LayoutParams(width / 4, width / 4));
                    imageView.setScaleType(ImageView.ScaleType.FIT_XY);
                    imageView.setColorFilter(null);
                } else {
                    imageView = (ImageView) convertView;
                }
                imageView.setImageBitmap(images[position]);

                return imageView;
            }
        });

        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                selection = position;
                pam_photo_id = IMAGE_FOLDERS[position];
                zoomImageFromThumb(v, position);
                onSubmit();
            }
        });
    }

    private void zoomImageFromThumb(final View thumbView, int position) {
        // If there's an animation in progress, cancel it
        // immediately and proceed with this one.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        // Load the high-resolution "zoomed-in" image.
        final ImageView expandedImageView = (ImageView) findViewById(
                R.id.expanded_image);
        expandedImageView.bringToFront();
//        expandedImageView.setImageResource(imageResId);
        expandedImageView.setImageBitmap(images[position]);

        // Calculate the starting and ending bounds for the zoomed-in image.
        // This step involves lots of math. Yay, math.
        final Rect startBounds = new Rect();
        final Rect finalBounds = new Rect();
        final Point globalOffset = new Point();

        // The start bounds are the global visible rectangle of the thumbnail,
        // and the final bounds are the global visible rectangle of the container
        // view. Also set the container view's offset as the origin for the
        // bounds, since that's the origin for the positioning animation
        // properties (X, Y).
        thumbView.getGlobalVisibleRect(startBounds);
        findViewById(R.id.container)
                .getGlobalVisibleRect(finalBounds, globalOffset);
        startBounds.offset(-globalOffset.x, -globalOffset.y);
        finalBounds.offset(-globalOffset.x, -globalOffset.y);

        // Adjust the start bounds to be the same aspect ratio as the final
        // bounds using the "center crop" technique. This prevents undesirable
        // stretching during the animation. Also calculate the start scaling
        // factor (the end scaling factor is always 1.0).
        float startScale;
        if ((float) finalBounds.width() / finalBounds.height()
                > (float) startBounds.width() / startBounds.height()) {
            // Extend start bounds horizontally
            startScale = (float) startBounds.height() / finalBounds.height();
            float startWidth = startScale * finalBounds.width();
            float deltaWidth = (startWidth - startBounds.width()) / 2;
            startBounds.left -= deltaWidth;
            startBounds.right += deltaWidth;
        } else {
            // Extend start bounds vertically
            startScale = (float) startBounds.width() / finalBounds.width();
            float startHeight = startScale * finalBounds.height();
            float deltaHeight = (startHeight - startBounds.height()) / 2;
            startBounds.top -= deltaHeight;
            startBounds.bottom += deltaHeight;
        }

        // Hide the thumbnail and show the zoomed-in view. When the animation
        // begins, it will position the zoomed-in view in the place of the
        // thumbnail.
        thumbView.setAlpha(0f);
        expandedImageView.setVisibility(View.VISIBLE);

        // Set the pivot point for SCALE_X and SCALE_Y transformations
        // to the top-left corner of the zoomed-in view (the default
        // is the center of the view).
        expandedImageView.setPivotX(0f);
        expandedImageView.setPivotY(0f);

        // Construct and run the parallel animation of the four translation and
        // scale properties (X, Y, SCALE_X, and SCALE_Y).
        AnimatorSet set = new AnimatorSet();
        set
                .play(ObjectAnimator.ofFloat(expandedImageView, View.X,
                        startBounds.left, finalBounds.left))
                .with(ObjectAnimator.ofFloat(expandedImageView, View.Y,
                        startBounds.top, finalBounds.top))
                .with(ObjectAnimator.ofFloat(expandedImageView, View.SCALE_X,
                        startScale, 1f)).with(ObjectAnimator.ofFloat(expandedImageView,
                View.SCALE_Y, startScale, 1f));
        set.setDuration(mShortAnimationDuration);
        set.setInterpolator(new DecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mCurrentAnimator = null;
            }
        });
        set.start();
        mCurrentAnimator = set;

        // Zoom back out after a delay
        final Handler handler = new Handler();
        final float startScaleFinal = startScale;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mCurrentAnimator != null) {
                    mCurrentAnimator.cancel();
                }

                // Animate the four positioning/sizing properties in parallel,
                // back to their original values.
                AnimatorSet set = new AnimatorSet();
                set.play(ObjectAnimator
                        .ofFloat(expandedImageView, View.X, startBounds.left))
                        .with(ObjectAnimator
                                .ofFloat(expandedImageView,
                                        View.Y,startBounds.top))
                        .with(ObjectAnimator
                                .ofFloat(expandedImageView,
                                        View.SCALE_X, startScaleFinal))
                        .with(ObjectAnimator
                                .ofFloat(expandedImageView,
                                        View.SCALE_Y, startScaleFinal));
                set.setDuration(mShortAnimationDuration);
                set.setInterpolator(new DecelerateInterpolator());
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        thumbView.setAlpha(1f);
                        expandedImageView.setVisibility(View.GONE);
                        mCurrentAnimator = null;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        thumbView.setAlpha(1f);
                        expandedImageView.setVisibility(View.GONE);
                        mCurrentAnimator = null;
                    }
                });
                set.start();
                mCurrentAnimator = set;
                loadImages();
                setupPAM();
            }
        }, 2500);

        // Make clickable, so a tap doesn't select another image.
        expandedImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Do nothing
            }
        });
    }

}
