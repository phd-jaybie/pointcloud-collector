package phd.jaybie.pointcloudcollector;

import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.FloatBuffer;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ArFragment fragment;

    private AutoCompleteTextView fileName;

    private PointerDrawable pointer = new PointerDrawable();
    private boolean isTracking;
    private boolean isHitting;

    private ModelRenderable andyRenderable;

    private List<Plane> PlaneList = new ArrayList<Plane>();

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");

    private Timestamp timestamp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        fileName = (AutoCompleteTextView) findViewById(R.id.fileText);
        setSupportActionBar(toolbar);

        fragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);

        final String[] SPACES = new String[] {
                "workstation", "reception", "apartment", "driveway", "hallway",
                "kitchen","stairwell"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line, SPACES);
        fileName.setAdapter(adapter);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Saving data to "+ fileName.getText().toString(),
                        Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                timestamp = new Timestamp(System.currentTimeMillis());

                savePlaneCloudToFile();
                savePointCloudToFile();
            }
        });

        fragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            fragment.onUpdate(frameTime);
            onUpdate();
            //savePlaneCloudToFile();
        });

        ModelRenderable.builder()
                .setSource(this, Uri.parse("andy.sfb"))
                .build()
                .thenAccept(renderable -> andyRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load andy renderable",
                                            Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });


        fragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (andyRenderable == null) {
                        return;
                    }
                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(fragment.getArSceneView().getScene());
                    // Create the transformable andy and add it to the anchor.
                    TransformableNode andy = new
                            TransformableNode(fragment.getTransformationSystem());
                    andy.setParent(anchorNode);
                    andy.setRenderable(andyRenderable);
                    andy.select();
                });
    }


    private void addPlaneToList(Plane planeTrackable){

        if (PlaneList.isEmpty()){
            PlaneList.add(planeTrackable);
            return;
        }

        Boolean addPlane = false;
        Plane subsumingPlane =  planeTrackable;
        Plane subsumedPlane = planeTrackable;

        Session session = fragment.getArSceneView().getSession();

        for (Plane listedPlane: PlaneList){

            subsumingPlane = listedPlane.getSubsumedBy();
            subsumedPlane = listedPlane;

            if (planeTrackable.equals(subsumingPlane)) {
                addPlane = false;
            } else {
                addPlane = true;
            }
        }

        if (addPlane) {
            PlaneList.add(planeTrackable);
            Log.i(TAG,"Added a new plane.");
        } else {
            PlaneList.remove(subsumedPlane);
            PlaneList.add(subsumingPlane);
            Log.i(TAG,"Replacing subsumed plane.");
        }

        return;
    }

    private void savePlaneCloudToFile() {

        File path = this.getApplicationContext().getExternalFilesDir(null);
        File file = new File(path,
                fileName.getText().toString() +"-"+ sdf.format(timestamp)+"-planecloud.txt");

        Session session = fragment.getArSceneView().getSession();

        try {
            FileOutputStream stream = new FileOutputStream(file);
            try {
                for (Plane listedPlane: session.getAllTrackables(Plane.class)){//PlaneList) {

                    FloatBuffer planePolygon = ((Plane) listedPlane).getPolygon();

                    Pose planePose = ((Plane) listedPlane).getCenterPose();

                    String planePolygonString = "Pose," + Float.toString(planePose.tx()) + ","
                            + (planePose.ty()) + "," + (planePose.tz()) +  ","
                            + (planePose.qx()) + "," + (planePose.qy()) + ","
                            + (planePose.qz()) + "," + (planePose.qw()) + "\n";

                    while (planePolygon.hasRemaining()) {
                        planePolygonString = planePolygonString + Float.toString(planePolygon.get()) + ","
                                + Float.toString(planePolygon.get()) + "\n";
                    }

                    planePolygonString = planePolygonString + "\n\n";

                    stream.write(planePolygonString.getBytes());
                }
                Log.i(TAG, "Successful savePlaneCloudToFile");
            } catch (Exception e1) {
                Log.e(TAG, "savePlaneCloudToFile;e1:" + e1.getMessage());
            } finally {
                stream.close();
            }
        } catch (Exception e2) {
            Log.e(TAG, "savePlaneCloudToFile;e2:" + e2.getMessage());
        }

        return;

    }

    private void savePointCloudToFile(){

        File path = this.getApplicationContext().getExternalFilesDir(null);
        File file = new File(path,
                fileName.getText().toString() + "-"+sdf.format(timestamp)+"-pointcloud.txt");

        Frame frame = fragment.getArSceneView().getArFrame();

        try (PointCloud pointCloud = frame.acquirePointCloud()){
            FloatBuffer pointcloudBuffer = pointCloud.getPoints();

            String pointcloudString = "";

            while (pointcloudBuffer.hasRemaining()) {
                pointcloudString = pointcloudString + Float.toString(pointcloudBuffer.get()) + ","
                        + Float.toString(pointcloudBuffer.get()) + ","
                        + Float.toString(pointcloudBuffer.get()) + ","
                        + Float.toString(pointcloudBuffer.get()) + "\n";
            }

            try {
                FileOutputStream stream = new FileOutputStream(file);
                try {
                    stream.write(pointcloudString.getBytes());
                    Log.i(TAG, "Successful savePointCloudToFile");
                } catch (Exception e1) {
                    Log.e(TAG, "savePointCloudToFile;e1:" + e1.getMessage());
                } finally {
                    stream.close();
                }
            } catch (Exception e2) {
                Log.e(TAG, "savePointCloudToFile;e2:" + e2.getMessage());
            }
        }

    }

    private void onUpdate() {
        boolean trackingChanged = updateTracking();
        View contentView = findViewById(android.R.id.content);
        if (trackingChanged) {
            if (isTracking) {
                contentView.getOverlay().add(pointer);
            } else {
                contentView.getOverlay().remove(pointer);
            }
            contentView.invalidate();
        }
        if (isTracking) {
            boolean hitTestChanged = updateHitTest();
            if (hitTestChanged) {
                pointer.setEnabled(isHitting);
                contentView.invalidate();
            }
        }
    }

    private boolean updateTracking() {
        Frame frame = fragment.getArSceneView().getArFrame();
        boolean wasTracking = isTracking;
        isTracking = frame != null &&
                frame.getCamera().getTrackingState() == TrackingState.TRACKING;
        return isTracking != wasTracking;
    }

    private boolean updateHitTest() {
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        boolean wasHitting = isHitting;
        isHitting = false;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    isHitting = true;

                    /**
                     * Insert code here that gets the Plane polygon
                     * **/

                    addPlaneToList((Plane) trackable);

                    break;

                }
            }
        }
        return wasHitting != isHitting;
    }
    private android.graphics.Point getScreenCenter() {
        View vw = findViewById(android.R.id.content);
        return new android.graphics.Point(vw.getWidth()/2, vw.getHeight()/2);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}