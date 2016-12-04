package updatemain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class UpdateLayerMain {

    public static void main(String[] args) {

        UpdateLayerMain ulm = new UpdateLayerMain();
        ulm.collectionPoint();

        for(int i = 1; i < 48;i++){
            ulm.getResasData(String.format("%02d", i));
        }
        // 福島県はデータなし
//        ulm.getResasData(String.format("07"));

    }

    /**
     * Json keys
     * */
    static String mAttributes = "attributes";
    static String mFeatures = "features";
    static String mP_NUMF2040 = "P_NUM_F2040";
    static String mValue = "value";

    /** polygonリスト */
    static Map<String,JSONObject> mPolygonList;
    static ArrayList<String> mPolygonListName;
    static ArrayList<JSONObject> mUpdateList;

    /** REST */
    // 更新レコード取得(全国のポリゴン情報)
    static String mCollectPolugonsReq = "http://services7.arcgis.com/903opF9LxIC4unCH/arcgis/rest/services/%E5%85%A8%E5%9B%BD%E5%B8%82%E5%8C%BA%E7%94%BA%E6%9D%91%E7%95%8C%E3%83%87%E3%83%BC%E3%82%BF_allJapanSatowaka/FeatureServer/0/query";
    // 全国の将来人口推計
    static String mResasRequest = "https://opendata.resas-portal.go.jp/api/v1-rc.1/population/future/cities";
    // 更新レコード取得(全国のポリゴン情報)
    static String mCollectPolyUpdate = "http://services7.arcgis.com/903opF9LxIC4unCH/arcgis/rest/services/%E5%85%A8%E5%9B%BD%E5%B8%82%E5%8C%BA%E7%94%BA%E6%9D%91%E7%95%8C%E3%83%87%E3%83%BC%E3%82%BF_allJapanSatowaka/FeatureServer/0/applyEdits";

    /**
     * polygon一覧を取得する
     * */
    private boolean collectionPoint(){

        // 観光スポットのポイントコレクションをRESTで取得
        try {
            String requestJson = createJsonRequest();
            JSONObject result = getRequest(mCollectPolugonsReq,requestJson);
            // call
            createRecestpointList(result);
        } catch (JsonGenerationException e) {
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
        } catch (JsonMappingException e) {
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
        } catch (IOException e) {
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
        }
        return true;
    }

    /**
     * 全部取得の条件
     * */
    private String createJsonRequest() throws JsonGenerationException, JsonMappingException, IOException{
        return "where=(1=1)&outFields=JCODE,FID&f=json&returnGeometry=false";
    }

    /**
     * 全部polygonをリストに格納する
     * attribures:FID,colectionpoint
     * geometry:xy
     * */
    private boolean createRecestpointList(JSONObject pJsonObject) throws JSONException{

        // JSONオブジェクトからリストを作成する
        JSONArray features = pJsonObject.getJSONArray(mFeatures);
        System.out.println("Response:" + "hogehoge");
        mPolygonList = new HashMap<String,JSONObject>();
        for(int i = 0 ; i < features.length();i++){
            mPolygonList.put(features.getJSONObject(i).getJSONObject(mAttributes)
                    .getString("JCODE"), features.getJSONObject(i));
        }
        return true;
    }

    // RESAS のデータを取得する
    private boolean getResasData(String pSichosonCode){

        // リクエストを作成
        String requestResasJson = createResasRequest(pSichosonCode);
        JSONObject resasResut = getResasRequest(mResasRequest, requestResasJson);
        System.out.println("satowaka");
        // updateList 作成
        makeUpdateList(resasResut);

        return true;
    }

    /**
     * RESASのリクエスト
     * */
    private String createResasRequest(String pPrefCode){
        return "year=2040&prefCode=" + pPrefCode;
    }

    // 更新情報(List)を作成する
    private void makeUpdateList(JSONObject pResasResut){
        mUpdateList = new ArrayList<JSONObject>();

        try {
            JSONArray resasResult =  pResasResut.getJSONObject("result").getJSONArray("cities");
            for(int i=0 ;i < resasResult.length();i++){

                String citycode = resasResult.getJSONObject(i).get("cityCode").toString();
                if(mPolygonList.containsKey(citycode)){
                    JSONObject jsonobj = mPolygonList.get(citycode);

                    JSONObject hogehoge = new JSONObject();
                    hogehoge.accumulate(mAttributes, jsonobj.getJSONObject(mAttributes).accumulate(mP_NUMF2040, resasResult.getJSONObject(i).get(mValue)));
                    mUpdateList.add(hogehoge);
                }
            }
            System.out.println("updateList : " + mUpdateList.toString());
            // call updater
            updateSpotCount();

        } catch (JSONException e) {
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
        }


    }
    // ArcGISを更新する。
    /**
     * 更新する
     * */
    private void updateSpotCount(){

        // データ準備
        String update = "f=json&updates=" + mUpdateList.toString();
        // REST CALL
        JSONObject result = getRequest(mCollectPolyUpdate,update);
        System.out.println("update Done ☆" + result.toString());

    }

    /**
     * REST POST CALL RESAS
     * */
    private JSONObject getResasRequest(String pStringUrl, String pRequestJson){

      HttpURLConnection con = null;
      String buffer = "";
      OutputStream os = null;
      BufferedReader reader = null;
      JSONObject response = null;

      try {
          URL url = new URL(pStringUrl+"?"+pRequestJson);
          con = (HttpURLConnection) url.openConnection();
          con.setRequestMethod("GET");
          con.setRequestProperty("Accept-Charset", "UTF-8");
          con.setRequestProperty("Content-type", "application/json");
          con.setRequestProperty("X-API-KEY", "XNRwjkiJhI8gSKTlRmird1Qg6rlU4eUc5oDtx0fr");
          con.setDoOutput(true);
          con.setDoInput(true);

          int status = con.getResponseCode();
          switch(status) {
              case HttpURLConnection.HTTP_OK:
                  InputStream is = con.getInputStream();
                  reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                  buffer = reader.readLine();
                  is.close();

                  String responseStr = buffer;
                  response = new JSONObject(buffer);
                  System.out.println("Response:" + responseStr);
                  return response;
              case HttpURLConnection.HTTP_UNAUTHORIZED:
                  break;

              default:
                  break;
          }
      } catch (Exception ex) {
          ex.printStackTrace();
      } finally {
          try {
              if (reader != null) {
                  reader.close();
              }
              if (os != null) {
                  os.close();
              }
              if (con != null) {
                  con.disconnect();
              }
          } catch (IOException e) {
              e.printStackTrace();
          }
      }
      // 空を返す
      return response;
    }

    /**
     * REST POST CALL
     * */
    private JSONObject getRequest(String pStringUrl, String pRequestJson){

      HttpURLConnection con = null;
      String buffer = "";
      OutputStream os = null;
      BufferedReader reader = null;
      JSONObject response = null;

      try {
          URL url = new URL(pStringUrl);
          con = (HttpURLConnection) url.openConnection();
          con.setRequestMethod("POST");
          con.setRequestProperty("Accept-Charset", "UTF-8");
          con.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
          con.setRequestProperty("Accept", "application/json");
          con.setDoOutput(true);
          con.setDoInput(true);

          //POST用のOutputStreamを取得
          os = con.getOutputStream();
          //POSTするデータ
          PrintStream ps = new PrintStream(os);
          ps.print(pRequestJson);
          ps.close();

          int status = con.getResponseCode();
          switch(status) {
              case HttpURLConnection.HTTP_OK:
                  InputStream is = con.getInputStream();
                  reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                  buffer = reader.readLine();
                  is.close();

                  String responseStr = buffer;
                  response = new JSONObject(buffer);
                  System.out.println("Response:" + responseStr);
                  return response;
              case HttpURLConnection.HTTP_UNAUTHORIZED:
                  break;

              default:
                  break;
          }
      } catch (Exception ex) {
          ex.printStackTrace();
      } finally {
          try {
              if (reader != null) {
                  reader.close();
              }
              if (os != null) {
                  os.close();
              }
              if (con != null) {
                  con.disconnect();
              }
          } catch (IOException e) {
              e.printStackTrace();
          }
      }
      return response;
    }

}
