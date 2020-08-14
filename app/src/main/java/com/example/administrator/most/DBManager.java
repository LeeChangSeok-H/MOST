package com.example.administrator.most;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;

public class DBManager{
    SQLiteDatabase db;
    Context context;
    // DBHelper 생성자로 관리할 DB 이름과 버전 정보를 받음
    public DBManager(Context context) {
        this.context = context;
        this.db = context.openOrCreateDatabase("MOSTDB", Activity.MODE_PRIVATE, null);
    }

    // DB를 새로 생성할 때 호출되는 함수
    public void create() {
        // 새로운 테이블 생성
        /* 이름은 MONEYBOOK이고, 자동으로 값이 증가하는 _id 정수형 기본키 컬럼과
        item 문자열 컬럼, price 정수형 컬럼, create_at 문자열 컬럼으로 구성된 테이블을 생성. */
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS MOST (");
        sql.append("_id INTEGER PRIMARY KEY AUTOINCREMENT,");
        sql.append("category TEXT NOT NULL,");
        sql.append("start TEXT NOT NULL,");
        sql.append("finish TEXT NOT NULL,");
        sql.append("during TEXT NOT NULL,");
        sql.append("information TEXT NOT NULL);");

        db.execSQL(sql.toString());
    }

    public void insert(String category, String start, String finish, String during, String information) {
        // 읽고 쓰기가 가능하게 DB 열기
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO MOST(category, start, finish, during, information) VALUES(");
        sql.append("'" + category + "',");
        sql.append("'" + start + "',");
        sql.append("'" + finish + "',");
        sql.append("'" + during + "',");
        sql.append("'" + information + "');");
        // DB에 입력한 값으로 행 추가
        db.execSQL(sql.toString());
    }

    public void drop() {
        // 입력한 항목과 일치하는 행 삭제
        db.execSQL("DROP TABLE MOST");
        db.close();
    }
    /*public void delete(String item) {
        SQLiteDatabase db = getWritableDatabase();
        // 입력한 항목과 일치하는 행 삭제
        db.execSQL("DELETE FROM MONEYBOOK WHERE item='" + item + "';");
        db.close();
    }*/

    public String getAllResults() {
        // 읽기가 가능하게 DB 열기
        StringBuilder result = new StringBuilder();

        // DB에 있는 데이터를 쉽게 처리하기 위해 Cursor를 사용하여 테이블에 있는 모든 데이터 출력
        Cursor cursor = db.rawQuery("SELECT * FROM MOST", null);
        // start + " - " + finish + " " + during + "분 활동 " + information +"\n";
        while (cursor.moveToNext()) {
            result.append(cursor.getString(2) + " - ");
            result.append(cursor.getString(3) + " ");
            if(cursor.getString(1).equals("활동")) result.append(cursor.getString(4) + "분 활동 ");
            else result.append(cursor.getString(4) + "분 체류 ");
            result.append(cursor.getString(5) + " \n");
        }

        return result.toString();
    }

    public String getMoveResult() {
        // category, start, finish, during, information
        // DB에 있는 데이터를 쉽게 처리하기 위해 Cursor를 사용하여 테이블에 있는 모든 데이터 출력
        Cursor cursor = db.rawQuery("SELECT during, information FROM MOST WHERE category = '활동'", null);
        int resultTime = 0, resultSteps = 0;

        while (cursor.moveToNext()) {
            resultTime += Integer.parseInt(cursor.getString(0));
            String info = cursor.getString(1);
            resultSteps += Integer.parseInt(info.substring(0, info.length()-3));
        }
        return resultTime +"-" + resultSteps;
    }

    public String getStayResult() {
        // 읽기가 가능하게 DB 열기
        StringBuilder result = new StringBuilder();
        // category, start, finish, during, information
        // DB에 있는 데이터를 쉽게 처리하기 위해 Cursor를 사용하여 테이블에 있는 모든 데이터 출력
        Cursor cursor = db.rawQuery("SELECT sum(cast(during as INTEGER)), information FROM MOST WHERE category = '체류' GROUP BY information", null);
        while (cursor.moveToNext()) {
            result.append(cursor.getString(1) + "-");
            result.append(cursor.getString(0) + "-");
        }
        return result.toString();
    }

    public void close() {
        db.close();
    }
}
