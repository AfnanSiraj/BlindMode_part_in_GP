package com.example.publictransportationguidance.tracking;

import static android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
import static com.example.publictransportationguidance.api.Api.NEO4J_BASE_URL;
import static com.example.publictransportationguidance.blindMode.speechToText.SpeechToTextHelper.convertHaaToTaaMarbuta;
import static com.example.publictransportationguidance.blindMode.textToSpeech.TextToSpeechHelper.convertMathIntoThoma;
import static com.example.publictransportationguidance.helpers.Functions.LISTEN_TO_CHOOSING_PATH_OR_NOT;
import static com.example.publictransportationguidance.helpers.Functions.LISTEN_TO_RAW_DESTINATION_NAME;
import static com.example.publictransportationguidance.helpers.Functions.LISTEN_TO_RAW_LOCATION_NAME;
import static com.example.publictransportationguidance.helpers.Functions.LISTEN_TO_SORTING_CRITERIA;
import static com.example.publictransportationguidance.helpers.Functions.LISTEN_TO_SPECIFIED_DESTINATION_NAME;
import static com.example.publictransportationguidance.helpers.Functions.LISTEN_TO_SPECIFIED_LOCATION_NAME;
import static com.example.publictransportationguidance.helpers.Functions.availableStopsToBeRead;
import static com.example.publictransportationguidance.helpers.Functions.calcEstimatedTripsTime;
import static com.example.publictransportationguidance.helpers.Functions.checkInternetConnectionToast;
import static com.example.publictransportationguidance.helpers.Functions.convertToAleph;
import static com.example.publictransportationguidance.helpers.Functions.execute;
import static com.example.publictransportationguidance.helpers.Functions.noAvailablePathsToast;
import static com.example.publictransportationguidance.helpers.Functions.removeCommas;
import static com.example.publictransportationguidance.helpers.Functions.sortingByCostToast;
import static com.example.publictransportationguidance.helpers.Functions.sortingByDistanceToast;
import static com.example.publictransportationguidance.helpers.Functions.sortingByTimeToast;
import static com.example.publictransportationguidance.helpers.Functions.stringIsFound;
import static com.example.publictransportationguidance.helpers.Functions.tryAgainToast;
import static com.example.publictransportationguidance.helpers.GlobalVariables.ARABIC;
import static com.example.publictransportationguidance.helpers.GlobalVariables.BUNDLE_PATH;
import static com.example.publictransportationguidance.helpers.GlobalVariables.CHOOSING_PATH_OR_NOT;
import static com.example.publictransportationguidance.helpers.GlobalVariables.COST;
import static com.example.publictransportationguidance.helpers.GlobalVariables.DISTANCE;
import static com.example.publictransportationguidance.helpers.GlobalVariables.INTENT_PATH;
import static com.example.publictransportationguidance.helpers.GlobalVariables.SELECTED_PATH;
import static com.example.publictransportationguidance.helpers.GlobalVariables.SORTING_CRITERIA;
import static com.example.publictransportationguidance.helpers.GlobalVariables.SORTING_CRITERIA_ACCEPTANCE;
import static com.example.publictransportationguidance.helpers.GlobalVariables.TIME;
import static com.example.publictransportationguidance.helpers.GlobalVariables.selectedPathNumberInWheel;
import static com.example.publictransportationguidance.helpers.PathsTokenizer.detailedPathToPrint;
import static com.example.publictransportationguidance.helpers.PathsTokenizer.getPathCost;
import static com.example.publictransportationguidance.helpers.PathsTokenizer.getPathDistance;
import static com.example.publictransportationguidance.helpers.PathsTokenizer.getStringPathToPopulateRoom;
import static com.example.publictransportationguidance.helpers.PathsTokenizer.listToArray;
import static com.example.publictransportationguidance.helpers.PathsTokenizer.enumeratePaths;
import static com.example.publictransportationguidance.helpers.PathsTokenizer.stopsAndMeans;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.example.publictransportationguidance.Fragments.HomeFragment;
import com.example.publictransportationguidance.blindMode.speechToText.SpeechToTextHelper;
import com.example.publictransportationguidance.blindMode.textToSpeech.TextToSpeechHelper;
import com.example.publictransportationguidance.pojo.pathsResponse.PathInfo;
import com.example.publictransportationguidance.api.RetrofitClient;
import com.example.publictransportationguidance.R;
import com.example.publictransportationguidance.room.DAO;
import com.example.publictransportationguidance.room.RoomDB;
import com.example.publictransportationguidance.pojo.pathsResponse.NearestPaths;
import com.example.publictransportationguidance.databinding.ActivityPathResultsBinding;
import com.example.publictransportationguidance.sharedPrefs.SharedPrefs;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PathResults extends AppCompatActivity implements TripsTimeCallback {
    private static List<PathInfo> TRANSPORTATION =null ; //Afnan: To use it in Blind Mode in Choosing a path
    private static String[] HeardTransportation;
    int wheelValue=0;           //Afnan: To use it in Blind Mode in Choosing a path
    String pathNumber;          //Afnan: To use it in Blind Mode in Choosing a path

    ActivityPathResultsBinding binding;
    DAO dao;

    /* M Osama: instance to put loadingDialog inFront of UI */
    LoadingDialog loadingDialog = new LoadingDialog(PathResults.this);

    /* M Osama: instance to deal with stt*/
    private SpeechToTextHelper speechToTextHelper;
    private TextToSpeechHelper textToSpeechHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_path_results);

        /* M Osama: initializing tts & stt */
        initializeTTSandSTT();

        String LOCATION = "",DESTINATION = "";
        /* M Osama: reading values from homeFragment */
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            LOCATION = extras.getString("LOCATION");
            DESTINATION = extras.getString("DESTINATION");
        }

        dao = RoomDB.getInstance(getApplication()).Dao();
        loadingDialog.startLoadingDialog();


        if(SharedPrefs.readMap("ON_BLIND_MODE",0)==1){
            textToSpeechHelper.speak(getString(R.string.SearchingForAvaliablePaths),()-> listenToNothing());
        }
        getNearestPaths(LOCATION,DESTINATION,this);

    }


    public void getNearestPaths(String location, String destination, TripsTimeCallback callback) {

        RetrofitClient.getInstance(NEO4J_BASE_URL).getApi().getNearestPaths(location,destination).enqueue(new Callback<List<List<NearestPaths>>>() {
            @Override
            public void onResponse(@NonNull Call<List<List<NearestPaths>>> call, @NonNull Response<List<List<NearestPaths>>> response) {
                List<List<NearestPaths>> paths = response.body();

                if (paths != null) {
                    if (paths.size() > 0) {
                        cacheToRoom(enumeratePaths(paths),paths, extractNodesLatLng(paths));        /* M Osama: give each Path a number then cache them to Room where each contains it's route nodes latLng*/
                        calcEstimatedTripsTime(paths,callback);
                        sortingOnClickListeners();                                                  /* M Osama: onClickListener for two sorting Buttons */
                    }
                    else { tryAgainToast(getApplicationContext());  noPathsFound();  noAvailablePathsToast(getApplicationContext()); }
                }
                else { checkInternetConnectionToast(getApplicationContext()); noPathsFound(); }
                loadingDialog.endLoadingDialog();
            }

            @Override
            public void onFailure(@NonNull Call<List<List<NearestPaths>>> call, @NonNull Throwable t) {
                noPathsFound();
                if(SharedPrefs.readMap("ON_BLIND_MODE",0)==1){
                    textToSpeechHelper.speak(getString(R.string.BadInternetConnection),()-> listenToNothing());
                }
                loadingDialog.endLoadingDialog();
            }
        });

    }

    @Override
    public void onTripsTimeCalculated(int[] globalDurations) {
        for (int pathNumber = 0; pathNumber < globalDurations.length; pathNumber++) {   dao.updatePathTime(pathNumber,globalDurations[pathNumber]); }
        updateViewsOnReSorting(sortPathsAscUsing(SORTING_CRITERIA));
    }

    public void cacheToRoom(HashMap pathMap, List<List<NearestPaths>> shortestPaths,ArrayList<ArrayList<LatLng>> pathsNodesLatLng){
        /*ToBe Deleted*/
        dao.deleteAllPaths();

        /* M Osama: caching Paths in Room (only if PathsTable is empty) */
        if (dao.getNumberOfRowsOfPathsTable() == 0) {
            for (int pathNum = 0; pathNum < pathMap.size(); pathNum++) {
                double tempDistance = getPathDistance(shortestPaths, pathNum);
                int tempCost = getPathCost(shortestPaths, pathNum);
                /* int tempTime is written down at onTripsTimeCalculated */
                String tempPath = getStringPathToPopulateRoom(pathMap).get(pathNum);
                String pickedPathDetails = detailedPathToPrint(stopsAndMeans(shortestPaths,pathNum));
                PathInfo pathInfo = new PathInfo(pathNum, tempDistance,tempCost, 0, tempPath,pickedPathDetails,pathsNodesLatLng.get(pathNum));
                dao.insertPath(pathInfo);
            }
        }
    }

    public void sortingOnClickListeners(){
        binding.sortByCost.setOnClickListener(v -> {        sortingByCostToast(this);       SORTING_CRITERIA=COST;     updateViewsOnReSorting(sortPathsAscUsing(COST));     });
        binding.sortByDistance.setOnClickListener(v -> {    sortingByDistanceToast(this);   SORTING_CRITERIA=DISTANCE; updateViewsOnReSorting(sortPathsAscUsing(DISTANCE)); });
        binding.sortByTime.setOnClickListener(v -> {        sortingByTimeToast(this);       SORTING_CRITERIA=TIME;     updateViewsOnReSorting(sortPathsAscUsing(TIME));     });
    }


    public void updateViewsOnReSorting(List<PathInfo> newSortedPaths){
        initCostDistanceTime(newSortedPaths);                                                /* M Osama: put cost,distance,time of the best root */
        setWheelSettings(combineSinglePathNodes(newSortedPaths));                            /* M Osama: Build String Array from cachedPath Info & pass it to build Wheel */
        wheelOnClickListener(newSortedPaths);                                                /* M Osama: onClickListener for wheel representing choices */
        costDistanceTimeTracker(newSortedPaths);

    }

    public void initCostDistanceTime(List<PathInfo> paths){
        binding.cost.setText(paths.get(0).getCost()+"");
        binding.distance.setText(paths.get(0).getDistance()+"");
        binding.time.setText(paths.get(0).getTime()+"");
    }

    public void setWheelSettings(String[] transportations) {
        binding.wheel.setMinValue(0);                                   /* M Osama: wheel populated starting from index0 from source*/
        binding.wheel.setMaxValue(transportations.length - 1);          /* M Osama: wheel populated till index(len-1)*/
        binding.wheel.setDisplayedValues(transportations);              /* M Osama: populating wheel with data */

        /*Afnan: Blind Mode*/
        if(SharedPrefs.readMap("ON_BLIND_MODE",0)==1) {
            binding.wheel.setValue(wheelValue);
            HeardTransportation= transportations;
            readingPaths();
        }
        else binding.wheel.setValue(0); /* M Osama: index0 content represent best result; to be edited after building database & mapping */
    }

    public void wheelOnClickListener(List<PathInfo> paths){
        binding.wheel.setOnClickListener((View v) -> {
            Intent selectedPathIntent = new Intent(PathResults.this, SelectedPath.class); /* haidy: to link between the 2 pages*/
            Bundle bundle = new Bundle();

            selectedPathNumberInWheel=binding.wheel.getValue();                                                              /* M Osama: to track which path the user chosen */

            bundle.putSerializable(BUNDLE_PATH,paths.get(selectedPathNumberInWheel).getCoordinates());                       /* M Osama: pass the nodes of selectedPath to be able to draw them on Map */
            selectedPathIntent.putExtra(INTENT_PATH,bundle);

            selectedPathIntent.putExtra(SELECTED_PATH,paths.get(selectedPathNumberInWheel).getDetailedPath());               /* M Osama: pass Path details to print them on screen */

            Toast.makeText(this, paths.get(selectedPathNumberInWheel).getPath(), Toast.LENGTH_SHORT).show();
            startActivity(selectedPathIntent);
        });
    }

    public void costDistanceTimeTracker(List<PathInfo> paths) {
        binding.wheel.setOnValueChangedListener((numberPicker, oldValue, newValue) -> updateCostDistanceTime(newValue,paths));
        binding.wheel.setOnScrollListener((numberPicker, scrollState) -> {if (scrollState == SCROLL_STATE_IDLE) updateCostDistanceTime(binding.wheel.getValue(),paths);});
    }

    private void updateCostDistanceTime(int pathNumber,List<PathInfo> paths) {
        PathInfo path = paths.get(pathNumber);
        binding.cost.setText(path.getCost()+"");
        binding.distance.setText(path.getDistance()+"");
        binding.time.setText(path.getTime()+"");
    }

    public ArrayList<ArrayList<LatLng>> extractNodesLatLng(List<List<NearestPaths>> shortestPaths){
        ArrayList<ArrayList<LatLng>> pathsNodesLatLng = new ArrayList<>();
        ArrayList<LatLng> tempPathNodesLatLng=new ArrayList<>();
        for(int pathNumber=0;pathNumber<shortestPaths.size();pathNumber++){
            List<NearestPaths> path = shortestPaths.get(pathNumber);
            for(int nodeNumber=0;nodeNumber<path.size();nodeNumber++) {
                NearestPaths node = path.get(nodeNumber);
                tempPathNodesLatLng.add(nodeNumber,new LatLng(node.getLatitude(),node.getLongitude()));
            }
            pathsNodesLatLng.add(pathNumber, tempPathNodesLatLng);
        }
        return pathsNodesLatLng;
    }


    /* M Osama: Build String Array from cachedPath Info */
    public String[] combineSinglePathNodes(List<PathInfo> paths){
        ArrayList<String> transportationsTemp = new ArrayList<>();
        for (int pathNum = 0; pathNum < dao.getNumberOfRowsOfPathsTable(); pathNum++) {
            transportationsTemp.add(paths.get(pathNum).getPath());
        }
        return listToArray(transportationsTemp);
    }

    public List<PathInfo> sortPathsAscUsing(String sortingCriteria){
        List<PathInfo> paths = dao.getSortedPathsASC(sortingCriteria);
        dao.clearAllPaths();
        dao.insertPaths(paths);

        TRANSPORTATION=paths; // Afnan : Blind Mode to use in Choosing a path
        return paths;
    }

    public void noPathsFound(){
        binding.cost.setText(R.string.NotAvailable);
        binding.distance.setText(R.string.NotAvailable);
        binding.time.setText(R.string.NotAvailable);

        String[] temp = new String[100];
        Arrays.fill(temp, getString(R.string.NoPathsFound));

        if(SharedPrefs.readMap("ON_BLIND_MODE",0)==1){
            textToSpeechHelper.speak(getString(R.string.NoPathsFound),()-> listenToNothing());
        }

        binding.wheel.setDisplayedValues(temp);
    }

    void initializeTTSandSTT(){
        textToSpeechHelper = TextToSpeechHelper.getInstance(this,ARABIC);
        speechToTextHelper = SpeechToTextHelper.getInstance(ARABIC);
    }

    private void listenToNothing(){}

    private void listenToChangingPath(PathResults pathResults) {
        speechToTextHelper.startSpeechRecognition(pathResults,LISTEN_TO_CHOOSING_PATH_OR_NOT);
    }

    //Afnan : Blind Mode Choosing a path
    public void readingPaths(){
        updateCostDistanceTime(binding.wheel.getValue(), TRANSPORTATION);
        pathNumber = Integer.toString(wheelValue + 1);
        if (SORTING_CRITERIA == COST) {
            textToSpeechHelper.speak("الطريق" + pathNumber + "و تكلفتهُ هيْ  " + binding.cost.getText() + "جنيهاً. سماع المسار. التالي. أَمْ إختيار", () -> listenToChangingPath(this));
        }
        else if (SORTING_CRITERIA == DISTANCE) {
            textToSpeechHelper.speak("الطريق" + pathNumber + "و مسافتهُ هيْ  " + binding.distance.getText() + "كيلومتراً. سماع المسار. التالي. أَمْ إختيار", () -> listenToChangingPath(this));
        }
        else if (SORTING_CRITERIA == TIME) {
            textToSpeechHelper.speak("الطريق" + pathNumber + "و سيستغرِقُ " + binding.time.getText() + "دقيقةً. سماع المسار. التالي. أَمْ إختيار", () -> listenToChangingPath(this));
        }

    }

    @Override
    /* Afnan: receiving answer from the user in Blind Mode */
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        speechToTextHelper.onActivityResult(requestCode, resultCode, data);                                         // Pass the onActivityResult event to the SpeechToTextHelper
        ArrayList<String> speechConvertedToText = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

        switch (requestCode) {
            case LISTEN_TO_CHOOSING_PATH_OR_NOT:
                String ChooseOrNot = convertHaaToTaaMarbuta(speechConvertedToText.get(0));
                if(stringIsFound(ChooseOrNot,CHOOSING_PATH_OR_NOT)) {
                    if (ChooseOrNot.equals(CHOOSING_PATH_OR_NOT[0]) || ChooseOrNot.equals(CHOOSING_PATH_OR_NOT[1]) || ChooseOrNot.equals(CHOOSING_PATH_OR_NOT[2])) {
                        ChoosingThePath(TRANSPORTATION);
                    }
                    else if(ChooseOrNot.equals(CHOOSING_PATH_OR_NOT[3]) || ChooseOrNot.equals(CHOOSING_PATH_OR_NOT[4])) {
                        wheelValue = (wheelValue + 1) % binding.wheel.getMaxValue();
                        binding.wheel.setValue(wheelValue);
                        pathNumber = Integer.toString(wheelValue + 1);
                        updateCostDistanceTime(binding.wheel.getValue(), TRANSPORTATION);
                        if (SORTING_CRITERIA == COST)
                            textToSpeechHelper.speak("الطريق" + pathNumber + "و تكلفتهُ هيْ  " + binding.cost.getText() + "جنيهاً. سماع المسار. التالي. أَمْ إختيار", () -> listenToChangingPath(this));
                        else if (SORTING_CRITERIA == DISTANCE) {
                            textToSpeechHelper.speak("الطريق" + pathNumber + "و مسافتهُ هيْ  " + binding.distance.getText() + "كيلومتراً. سماع المسار. التالي. أَمْ إختيار", () -> listenToChangingPath(this));
                        } else if (SORTING_CRITERIA == TIME) {
                            textToSpeechHelper.speak("الطريق" + pathNumber + "و سيستغرِقُ " + binding.time.getText() + "دقيقةً. سماع المسار. التالي. أَمْ إختيار", () -> listenToChangingPath(this));
                        }
                    }
                    else if(ChooseOrNot.equals(CHOOSING_PATH_OR_NOT[5])|| ChooseOrNot.equals(CHOOSING_PATH_OR_NOT[6])|| ChooseOrNot.equals(CHOOSING_PATH_OR_NOT[7])|| ChooseOrNot.equals(CHOOSING_PATH_OR_NOT[8]))
                    {
                        textToSpeechHelper.speak("مسارك هو. " + convertMathIntoThoma(HeardTransportation[binding.wheel.getValue()])+"."+" إختيار أَمْ التالي ", () -> listenToChangingPath(this));
                    }
                }
                else
                    textToSpeechHelper.speak("عفوا !", () -> listenToChangingPath(this));

        }
    }

    public void ChoosingThePath(List<PathInfo> paths){

        Intent selectedPathIntent = new Intent(PathResults.this, SelectedPath.class);
        Bundle bundle = new Bundle();

        selectedPathNumberInWheel=binding.wheel.getValue();

        bundle.putSerializable(BUNDLE_PATH,paths.get(selectedPathNumberInWheel).getCoordinates());
        selectedPathIntent.putExtra(INTENT_PATH,bundle);

        selectedPathIntent.putExtra(SELECTED_PATH,paths.get(selectedPathNumberInWheel).getDetailedPath());
        startActivity(selectedPathIntent);

    }

}



/** M Osama:  Old functions to be deleted*/
//    public List<PathInfo> sortPathsDescUsing(String sortingCriteria){
//        List<PathInfo> paths = dao.getSortedPathsDESC(sortingCriteria);
//        dao.clearAllPaths();
//        dao.insertPaths(paths);
//        return paths;
//    }