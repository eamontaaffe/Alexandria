package it.jaschke.alexandria;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import it.jaschke.alexandria.CameraPreview.CameraPreview;
import it.jaschke.alexandria.data.AlexandriaContract;
import it.jaschke.alexandria.services.BookService;
import it.jaschke.alexandria.services.DownloadImage;


public class AddBook extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String LOG_TAG = AddBook.class.getSimpleName();
    private EditText ean;
    private final int LOADER_ID = 1;
    private View rootView;
    private Camera mCamera = null;
    private CameraPreview mCameraPreview = null;
    private ImageScanner mScanner;
    private Handler autoFocusHandler;

    private final String EAN_CONTENT="eanContent";
    private static final String SCAN_FORMAT = "scanFormat";
    private static final String SCAN_CONTENTS = "scanContents";

    private String mScanFormat = "Format:";
    private String mScanContents = "Contents:";



    public AddBook(){
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(ean!=null) {
            outState.putString(EAN_CONTENT, ean.getText().toString());
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_add_book, container, false);
        ean = (EditText) rootView.findViewById(R.id.ean);

        ean.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //no need
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //no need
            }

            @Override
            public void afterTextChanged(Editable s) {
                submitEan(s.toString());
            }
        });

        rootView.findViewById(R.id.scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ean.setText("");
                clearFields();

            }
        });

        rootView.findViewById(R.id.save_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ean.setText("");
            }
        });

        rootView.findViewById(R.id.delete_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent bookIntent = new Intent(getActivity(), BookService.class);
                bookIntent.putExtra(BookService.EAN, ean.getText().toString());
                bookIntent.setAction(BookService.DELETE_BOOK);
                getActivity().startService(bookIntent);
                ean.setText("");
            }
        });

        if(savedInstanceState!=null){
            ean.setText(savedInstanceState.getString(EAN_CONTENT));
            ean.setHint("");
        }

        ((SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout))
                .setPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
                    @Override
                    public void onPanelSlide(View view, float v) {

                    }

                    @Override
                    public void onPanelCollapsed(View view) {
                        if(mCamera != null) {
                            mCamera.setPreviewCallback(mPreviewCb);
                            mCamera.startPreview();
                        }
                    }

                    @Override
                    public void onPanelExpanded(View view) {
                        if(mCamera != null) {
                            mCamera.setPreviewCallback(null);
                            mCamera.stopPreview();
                        }
                    }

                    @Override
                    public void onPanelAnchored(View view) {

                    }

                    @Override
                    public void onPanelHidden(View view) {

                    }
                });

        return rootView;
    }

    private void restartLoader(){
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    private void submitEan(String ean) {
        //catch isbn10 numbers
        if(ean.length()==10 && !ean.startsWith("978")){
            ean="978"+ean;
        }
        if(ean.length()<13){
            // TODO BUG FIX for Hsiao-Lu feedback
            // It shouldn't clear the fields unless a new valid isbn is entered.
            clearFields();
            return;
        }
        //Once we have an ISBN, start a book intent
        Intent bookIntent = new Intent(getActivity(), BookService.class);
        bookIntent.putExtra(BookService.EAN, ean);
        bookIntent.setAction(BookService.FETCH_BOOK);
        getActivity().startService(bookIntent);
        AddBook.this.restartLoader();
    }

    @Override
    public android.support.v4.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if(ean.getText().length()==0){
            return null;
        }
        String eanStr= ean.getText().toString();
        if(eanStr.length()==10 && !eanStr.startsWith("978")){
            eanStr="978"+eanStr;
        }
        return new CursorLoader(
                getActivity(),
                AlexandriaContract.BookEntry.buildFullBookUri(Long.parseLong(eanStr)),
                null,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(android.support.v4.content.Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()) {
            return;
        }

        //BUGFIX if some of the fields are empty it will throw a NullPointerException
        try {
            String bookTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.TITLE));
            ((TextView) rootView.findViewById(R.id.bookTitle)).setText(bookTitle);

            String bookSubTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.SUBTITLE));
            ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText(bookSubTitle);

            String authors = data.getString(data.getColumnIndex(AlexandriaContract.AuthorEntry.AUTHOR));

            String[] authorsArr = authors.split(",");
            ((TextView) rootView.findViewById(R.id.authors)).setLines(authorsArr.length);
            ((TextView) rootView.findViewById(R.id.authors)).setText(authors.replace(",", "\n"));
            String imgUrl = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.IMAGE_URL));
            if (Patterns.WEB_URL.matcher(imgUrl).matches()) {
                new DownloadImage((ImageView) rootView.findViewById(R.id.bookCover)).execute(imgUrl);
                rootView.findViewById(R.id.bookCover).setVisibility(View.VISIBLE);
            }

            String categories = data.getString(data.getColumnIndex(AlexandriaContract.CategoryEntry.CATEGORY));
            ((TextView) rootView.findViewById(R.id.categories)).setText(categories);

            String desc = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.DESC));
            ((TextView) rootView.findViewById(R.id.fullBookDesc)).setText(desc);
        } catch (NullPointerException e) {
            Log.e(LOG_TAG,e.getMessage(),e);
        }

        rootView.findViewById(R.id.save_button).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.delete_button).setVisibility(View.VISIBLE);

        //TODO slide up the sliding panel and enable panel touch
        ((SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout))
                .setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {

    }

    private void clearFields(){
        ((TextView) rootView.findViewById(R.id.bookTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.authors)).setText("");
        ((TextView) rootView.findViewById(R.id.categories)).setText("");
        ((TextView) rootView.findViewById(R.id.fullBookDesc)).setText("");
        rootView.findViewById(R.id.bookCover).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.save_button).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.delete_button).setVisibility(View.INVISIBLE);

        //TODO disable panel touch and change heading to "Scan a barcode"
        ((SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout))
                .setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);

        if(mCamera != null) {
            mCamera.setPreviewCallback(mPreviewCb);
            mCamera.startPreview();
        }

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        activity.setTitle(R.string.scan);
    }

    @Override
    public void onResume() {
        super.onResume();
        //Open the camera and display a preview

        if (mCamera == null) {
            Log.v(LOG_TAG, "OpenCameraAsyncTask.execute");
            new OpenCameraAsyncTask().execute();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private class OpenCameraAsyncTask extends AsyncTask<Void, Void, Camera> {
        @Override
        protected Camera doInBackground(Void... voids) {
            try{
                //TODO add a setting for camera selection
                Camera camera = Camera.open();//you can use open(int) to use different cameras
                return camera;
            } catch (Exception e){
                Log.d("ERROR", "Failed to get camera: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Camera camera) {
            super.onPostExecute(camera);
            mCamera = camera;

            //Set focus mode to continous
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            mCamera.setParameters(parameters);

            if(mCamera != null) {
                mCameraPreview = new CameraPreview(getActivity(), mCamera, mPreviewCb/*, mAutoFocusCB*/);//create a SurfaceView to show camera data
                FrameLayout cameraView = (FrameLayout)rootView.findViewById(R.id.camera_view);

                if (cameraView != null)
                    cameraView.addView(mCameraPreview);//add the SurfaceView to the layout
            }

            /* Instance barcode scanner */
            mScanner = new ImageScanner();
            mScanner.setConfig(0, Config.X_DENSITY, 3);
            mScanner.setConfig(0, Config.Y_DENSITY, 3);
        }
    }

    Camera.PreviewCallback mPreviewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            Image barcode = new Image(size.width, size.height, "Y800");
            barcode.setData(data);

            int result = mScanner.scanImage(barcode);

            if (result != 0) {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();

                SymbolSet syms = mScanner.getResults();
                for (Symbol sym : syms) {
                    Toast.makeText(getActivity(),"ISBN: " + sym.getData(),Toast.LENGTH_LONG).show();
                    ean = (EditText) rootView.findViewById(R.id.ean);
                    ean.setText(sym.getData());
                }
            }
        }
    };
}
