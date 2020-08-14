package com.example.administrator.most;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Administrator on 2018-06-07.
 */

public class TrackingActivity extends AppCompatActivity implements LocationListener  {
    static final int INDOOR_ROM_401 = 0, INDOOR_DASAN_LOBBY = 1;
    static final int OUTDOOR_FIELD = 2, OUTDOOR_BENCH = 3;
    static final int INDOOR_UNKWON = 4, OUTDOOR_UNKWON = 5;
    static final double STANDARD_ACCURACY = 30;

    static final int STATE_MOVE = 6, STATE_STAY = 7, STATE_NONE = 8;
    static final int FLAG_INDOOR = 9, FLAG_OUTDOOR = 10;

    int userState = STATE_NONE;
    DBManager dbManager;

    LocationManager mLocationManager;
    int DOOR_FLAG = -1;
    int getLocationCount = 0;
    double minAccuracy = 987654321;

    Place room_401, dasan_lobby, field,  bench;
    // 와이파이 스캔 결과를 담을 리스트 객체 선언
    List<ScanResult> scanResultList;
    // 와이파이 사용을 위한 와이파이매니저 객체 선언
    WifiManager wifiManager;
    Context context = this;

    SimpleDateFormat dateForamt = new SimpleDateFormat("HH:mm");
    String stayPlace = null;
    long moveStartTime = -1, stayStartTime = -1;
    long moveFinishTime = -1, stayFinishTime = -1;

    TextView logTextView;

    TextfileManager textfileManager;    // 파일 매니져 선언

    int sum_step = 0 ;   // 총 걸음 수
    int sum_moving = 0; // 총 움직임 시간
    int topplace_time = 0;  // 가장 오래 머문 곳의 시간
    String top_place = "None";   // 탑 플레이스

    // 장소들의 시간을 저장할 배열 : 0 - 401호, 1 - 다산, 2 - 벤치, 3 - 운동장
    int placetime[] = new int[4];


    TextView ResultTextView;

    // Wifi scan 결과를 받는 용도로 사용하는 Broadcast Recevier
    BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // 와이파이 스캔 결과를 얻을 수 있을 때
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                reset(FLAG_INDOOR);
                if (userState == STATE_STAY) {
                    scanResultList = wifiManager.getScanResults();
                    HashMap<String, Integer> nowPlaceInfoHash = new HashMap<>();
                    for (int i = 0; i < scanResultList.size(); i++) {
                        ScanResult result = scanResultList.get(i);
                        nowPlaceInfoHash.put(result.BSSID, result.level);
                    }
                    int place = findIndoorPlace(nowPlaceInfoHash);

                    if (place == INDOOR_ROM_401) {
                        stayPlace = "2공학관 401호";
                        Toast.makeText(context, "2공학관 401호", Toast.LENGTH_SHORT).show();
                    } else if (place == INDOOR_DASAN_LOBBY) {
                        stayPlace = "다산 로비";
                        Toast.makeText(context, "다산 로비", Toast.LENGTH_SHORT).show();
                    } else if (place == INDOOR_UNKWON) {
                        stayPlace = "실내";
                        Toast.makeText(context, "실내", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };

    BroadcastReceiver mostReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(action.equals(MOSTService.ACTION_MOVE)) {
                Log.d("TrackingActivity", "MOVE ACTION");
                if(userState == STATE_NONE) moveStartTime = getNowTimeLong();

                if(userState == STATE_STAY) {
                    long nowTime = getNowTimeLong();
                    stayFinishTime = nowTime;
                    moveStartTime = nowTime;

                    String start = getTimeString(stayStartTime);
                    String finish = getTimeString(stayFinishTime);
                    String during = String.valueOf(getDuring(start, finish));
                    String information = stayPlace;
                    String log = start + " - " + finish + " " + during + "분 체류 " + information +"\n";

                    if(information.equals("2공학관 401호")){
                        placetime[0] += Integer.parseInt(during);
                    }
                    else if(information.equals("다산 로비")){
                        placetime[1] += Integer.parseInt(during);
                    }
                    else if(information.equals("벤치")){
                        placetime[2] += Integer.parseInt(during);
                    }
                    else if(information.equals("운동장")){
                        placetime[3] += Integer.parseInt(during);
                    }
                    else if(information.equals("실외")){
                        placetime[3] += Integer.parseInt(during);
                    }


                    topplace();

                    // 텍스트 뷰 갱신
                    ResultTextView.setText("\nMoving time : " + sum_moving + "분\nSteps : " + sum_step + "걸음\nTopPlace : "+top_place + "\n" );

//                    // 현재 들어온 장소에 체류한 시간이 이전의 체류한 곳의 시간 보다 많으면 탑 플레이스로 지정
//                    if(topplace_time < Integer.parseInt(during)){
//                        top_place = information;    // 탑 플레이스 갱신
//                        // 텍스트 뷰에 보여줌
//                        ResultTextView.setText("\nMoving time : " + sum_moving + "분\nSteps : " + sum_step + "걸음\nTopPlace : "+top_place + "\n" );
//                        // 탑 플레이스의 체류 시간 갱신
//                        topplace_time = Integer.parseInt(during);
//                        if(information.equals("실내") == false){
//                            if(information.equals("실외") == false){
//                                ResultTextView.setText("Moving time : " + sum_moving + "Steps : " + sum_step + "TopPlace : " );
//                            }
//                        }
//                    }
                    dbManager.insert("체류", start, finish, during, information);
                    logTextView.append(log);

                    textfileManager.save(log);  // 파일 저장
                    Toast.makeText(context, log, Toast.LENGTH_SHORT).show();

                    stayPlace = null;
                }
                userState = STATE_MOVE;
            }

            if(action.equals(MOSTService.ACTION_STAY)) {
                Log.d("TrackingActivity", "STAY ACTION");
                if(userState == STATE_NONE) stayStartTime = getNowTimeLong();

                if(userState == STATE_MOVE) {
                    long nowTime = getNowTimeLong();
                    stayStartTime = nowTime;
                    moveFinishTime = nowTime;

                    int steps = intent.getIntExtra("steps", 0);
                    if (steps > 0) {
                        String start = getTimeString(moveStartTime);
                        String finish = getTimeString(moveFinishTime);
                        String during = String.valueOf(getDuring(start, finish));

                        // 총 걸음과 총 움직임 시간 증가
                        sum_step += steps;
                        sum_moving += Integer.parseInt(during);

                        String information = steps + " "+"걸음";
                        String log = start + " - " + finish + " " + during + "분 활동 " + information +"\n";

                        dbManager.insert("활동", start, finish, during, information);
                        logTextView.append(log);
                        // 텍스트 뷰를 갱신한 내용으로 바꿈
                        ResultTextView.setText("\nMoving time : " + sum_moving + "분\nSteps : " + sum_step + "걸음\nTopPlace : " + top_place + "\n" );
                        textfileManager.save(log);  // 파일 저장
                        Toast.makeText(context, log, Toast.LENGTH_SHORT).show();
                    }
                }
                userState = STATE_STAY;
                setProvider();
            }
        }
    };

    public String getDuring(String start, String finish) {
        int startHour, startMin;
        int finishHour, finishMin;
        int duringHour, duringMin;

        String[] starts = start.split(":");
        String[] finishs = finish.split(":");

        startHour = Integer.parseInt(starts[0]);
        startMin  = Integer.parseInt(starts[1]);

        finishHour = Integer.parseInt(finishs[0]);
        finishMin  = Integer.parseInt(finishs[1]);

        duringHour = finishHour - startHour;
        duringMin  = finishMin - startMin;

        return String.valueOf(duringHour * 60 + duringMin);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);

        logTextView = (TextView)findViewById(R.id.TRACKING_TEXTVIEW);
        ResultTextView = (TextView)findViewById(R.id.RESULT_TEXTVIEW);
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);

        if (!wifiManager.isWifiEnabled())
            wifiManager.setWifiEnabled(true);

        IntentFilter mostFilter = new IntentFilter();
        mostFilter.addAction(MOSTService.ACTION_MOVE);
        mostFilter.addAction(MOSTService.ACTION_STAY);
        registerReceiver(mostReceiver, mostFilter);

        makePlaces();

        dbManager = new DBManager(this);
        dbManager.create();

        String savedLog = dbManager.getAllResults();
        logTextView.setText(savedLog);

        // 각 장소의 시간을 0으로 초기화
        for(int k=0 ; k<placetime.length ; k++){
            placetime[k] = 0;
        }

        // 걸음 수와 움직인 시간을 가져옴 : 총 움직인 시간 - 총 걸음 수
        String moveResult = dbManager.getMoveResult();
        // 장소별로 장소 이름과 해당 장소의 체류 시간을 가져옴 : 장소이름 - 체류시간 - 장소이름 - 체류시간
        String stayResult = dbManager.getStayResult();

        // -로 구별해서 배열로 저장
        String[] moveResult_split = moveResult.split("-");
        sum_moving += Integer.parseInt(moveResult_split[0]); // 총 움직임 시간
        sum_step += Integer.parseInt(moveResult_split[1]);  // 총 걸음 수

        // -로 구별해서 배열로 저장
        String[] stayResult_split = stayResult.split("-");

        for(int i=0 ; i< stayResult_split.length ; i++){
            Toast.makeText(this, i+stayResult_split[i],Toast.LENGTH_SHORT).show();
            if(stayResult_split[i].equals("2공학관 401호")){
                placetime[0] += Integer.parseInt(stayResult_split[i+1]);
            }
            else if(stayResult_split[i].equals("다산 로비")){
                placetime[1] += Integer.parseInt(stayResult_split[i+1]);
            }
            else if(stayResult_split[i].equals("벤치")){
                placetime[2] += Integer.parseInt(stayResult_split[i+1]);
            }
            else if(stayResult_split[i].equals("운동장")){
                placetime[3] += Integer.parseInt(stayResult_split[i+1]);
            }
            else if(stayResult_split[i].equals("실외")){
                placetime[3] += Integer.parseInt(stayResult_split[i+1]);
            }
        }
        topplace();
        // 텍스트 뷰를 갱신한 내용으로 바꿈
        ResultTextView.setText("\nMoving time : " + sum_moving + "분\nSteps : " + sum_step + "걸음\nTopPlace : " + top_place + "\n" );

        textfileManager = new TextfileManager();    // 파일 매니져 클래스 선언

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        unregisterMostReceiver();
        Log.d("TrackingActivity", "onDestroy!");
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        Log.d("TrackingActivity", "onStop!");
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d("TrackingActivity", "onPause");
        super.onPause();
    }

    private void unregisterScanReceiver() {
        if(scanReceiver != null) {
            try {
                unregisterReceiver(scanReceiver);
            }
            catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    private void unregisterMostReceiver() {
        if(mostReceiver != null) {
            unregisterReceiver(mostReceiver);
            mostReceiver = null;
        }
    }
    public void makePlaces() {
        // 401호 생성
        HashMap<String, Integer> indoorInfoHash_401 = new HashMap<>();
        indoorInfoHash_401.put("64:e5:99:db:05:cc",-49);
        indoorInfoHash_401.put("40:01:7a:de:11:3f",-79);
        indoorInfoHash_401.put("40:01:7a:de:11:3e",-79);
        indoorInfoHash_401.put("00:08:9f:52:b0:e4",-64);
        indoorInfoHash_401.put("40:01:7a:de:11:30",-59);
        indoorInfoHash_401.put("40:01:7a:de:11:31",-58);
        indoorInfoHash_401.put("40:01:7a:de:11:3d",-79);
        indoorInfoHash_401.put("64:e5:99:db:05:c8",-51);
        indoorInfoHash_401.put("18:80:90:c6:7b:20",-46);
        indoorInfoHash_401.put("18:80:90:c6:7b:22",-46);
        indoorInfoHash_401.put("18:80:90:c6:7b:21",-46);
        indoorInfoHash_401.put("18:80:90:c6:7b:2f",-61);
        indoorInfoHash_401.put("18:80:90:c6:7b:2e",-61);
        indoorInfoHash_401.put("20:3a:07:48:58:d5",-70);
        indoorInfoHash_401.put("20:3a:07:48:58:de",-85);
        indoorInfoHash_401.put("20:3a:07:48:58:df",-85);
        indoorInfoHash_401.put("88:36:6c:6a:95:b2",-68);
        indoorInfoHash_401.put("88:36:6c:6a:96:f8",-80);
        indoorInfoHash_401.put("64:e5:99:2c:ef:36",-62);
        room_401 = Place.builder()
                .name("2공학관 401")
                .flag(Place.FLAG_INDOOR)
                .indoorInfoHash(indoorInfoHash_401)
                .build();
        room_401.setContext(context);

        // 다산로비 생성
        HashMap<String, Integer> indoorInfoHash_lobby = new HashMap<>();
        indoorInfoHash_lobby.put("20:3a:07:49:5c:ef", -66);
        indoorInfoHash_lobby.put("20:3a:07:49:5c:ee", -66);
        indoorInfoHash_lobby.put("a4:18:75:58:77:da", -70);
        indoorInfoHash_lobby.put("20:3a:07:9e:a6:c5", -55);
        indoorInfoHash_lobby.put("20:3a:07:9e:a6:ca", -59);
        indoorInfoHash_lobby.put("20:3a:07:9e:a6:cf", -59);
        indoorInfoHash_lobby.put("20:3a:07:9e:a6:ce", -59);
        indoorInfoHash_lobby.put("34:a8:4e:6a:44:6f", -72);
        indoorInfoHash_lobby.put("34:a8:4e:6a:44:6e", -72);
        indoorInfoHash_lobby.put("34:a8:4e:6a:44:6a", -72);
        indoorInfoHash_lobby.put("a4:18:75:58:77:d5", -59);
        indoorInfoHash_lobby.put("a4:18:75:58:77:df", -71);
        indoorInfoHash_lobby.put("64:d9:89:46:4b:ef", -83);
        indoorInfoHash_lobby.put("64:d9:89:46:4b:ee", -81);
        indoorInfoHash_lobby.put("34:a8:4e:6a:44:65", -70);
        indoorInfoHash_lobby.put("88:75:56:c7:1f:1e", -78);
        dasan_lobby = Place.builder()
                .name("다산정보관 1층 로비")
                .flag(Place.FLAG_INDOOR)
                .indoorInfoHash(indoorInfoHash_lobby)
                .build();
        dasan_lobby.setContext(context);

        // 운동장 생성
        field = Place.builder()
                .name("운동장")
                .latitude(36.762581)
                .longitude(127.284527)
                .flag(Place.FLAG_OUTDOOR)
                .radius(80)
                .build();
        field.setContext(context);

        // 벤치 생성
        bench = Place.builder()
                .name("대학본부 앞 잔디광장 벤치")
                .latitude(36.764215)
                .longitude(127.282173)
                .flag(Place.FLAG_OUTDOOR)
                .radius(50)
                .build();
        bench.setContext(context);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menus_tracking, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_stop :
                Toast.makeText(this, "Tracking Stop!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(TrackingActivity.this, MainActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void setProvider() {
        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, this);
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public void reset(int flag) {
        minAccuracy = 987654321;
        DOOR_FLAG = -1;
        getLocationCount = 0;
        mLocationManager.removeUpdates(this);
        if(flag == FLAG_INDOOR) unregisterScanReceiver();
    }

    public void onLocationChanged(Location location) {

        if(getLocationCount < 10) {
            minAccuracy = Math.min(minAccuracy, location.getAccuracy());
            getLocationCount++;
            return;
        }

        else {
            if(minAccuracy >= STANDARD_ACCURACY) DOOR_FLAG = FLAG_INDOOR;
            else DOOR_FLAG = FLAG_OUTDOOR;
            mLocationManager.removeUpdates(this);
        }

        if(DOOR_FLAG == FLAG_OUTDOOR) {
            int place = findOutdoorPlace(location.getLatitude(), location.getLongitude());
            reset(FLAG_OUTDOOR);
            if (place == OUTDOOR_FIELD) {
                stayPlace = "운동장";
                Toast.makeText(this, "운동장", Toast.LENGTH_SHORT).show();
            } else if (place == OUTDOOR_BENCH) {
                stayPlace = "벤치";
                Toast.makeText(this, "벤치", Toast.LENGTH_SHORT).show();
            } else if (place == OUTDOOR_UNKWON) {
                stayPlace = "실외";
                Toast.makeText(this, "실외", Toast.LENGTH_SHORT).show();
            }
        }

        else if(DOOR_FLAG == FLAG_INDOOR) {
            IntentFilter scanFilter = new IntentFilter();
            scanFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            registerReceiver(scanReceiver, scanFilter);
            wifiManager.startScan();
        }
    }

    public int findIndoorPlace(HashMap<String, Integer> nowPlaceInfoHash) {
        if(room_401.isEqualndoorPlace(nowPlaceInfoHash)) return INDOOR_ROM_401;
        if(dasan_lobby.isEqualndoorPlace(nowPlaceInfoHash)) return INDOOR_DASAN_LOBBY;
        return INDOOR_UNKWON;
    }

    public int findOutdoorPlace(double latitude, double longitude) {
        if(field.isEqualOutdoorPlace(latitude, longitude)) return OUTDOOR_FIELD;
        if(bench.isEqualOutdoorPlace(latitude, longitude)) return OUTDOOR_BENCH;
        return OUTDOOR_UNKWON;
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {

    }
    public void onProviderEnabled(String provider) {

    }
    public void onProviderDisabled(String provider) {
    }

    public void onClickCheck(View v) {
        IntentFilter scanFilter = new IntentFilter();
        scanFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(scanReceiver, scanFilter);
        userState = STATE_STAY;
        setProvider();
    }

    public long getNowTimeLong() {
        return Calendar.getInstance().getTimeInMillis();
    }

    public String getTimeString(long time) {
        Date date = new Date(time);
        return dateForamt.format(date);
    }

    public void topplace(){
        int max = placetime[0];
        int num = 0;
        for(int j= 1 ; j<4 ; j++){
            if(placetime[j] >= max){
                max = placetime[j];
                num = j;
            }
        }
        if(max == 0){
            top_place = "None";
        }else{
            if( num == 0){
                top_place = "2공학관 401호 ";
            }else if( num == 1){
                top_place = "다산 로비 ";
            }else if( num == 2){
                top_place = "벤치 ";
            }else if( num == 3){
                top_place = "운동장 ";
            }
        }


    }
}
