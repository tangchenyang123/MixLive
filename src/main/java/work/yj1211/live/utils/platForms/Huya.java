package work.yj1211.live.utils.platForms;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import work.yj1211.live.utils.Global;
import work.yj1211.live.utils.HttpUtil;
import work.yj1211.live.utils.http.HttpContentType;
import work.yj1211.live.utils.http.HttpRequest;
import work.yj1211.live.vo.LiveRoomInfo;
import work.yj1211.live.vo.Owner;
import work.yj1211.live.vo.platformArea.AreaInfo;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Huya {
    private static final Pattern PATTERN = Pattern.compile("\"liveLineUrl\":\"([\\s\\S]*?)\",");
    private static final Pattern PATTERN2 = Pattern.compile("\"lUid\":([\\s\\S]*?),");
    private static final Pattern OwnerName = Pattern.compile("\"sNick\":\"([\\s\\S]*?)\",");
    private static final Pattern RoomName = Pattern.compile("\"sRoomName\":\"([\\s\\S]*?)\",");
    private static final Pattern RoomPic = Pattern.compile("\"sScreenshot\":\"([\\s\\S]*?)\",");
    private static final Pattern OwnerPic = Pattern.compile("\"sAvatar180\":\"([\\s\\S]*?)\",");
    private static final Pattern AREA = Pattern.compile("\"sGameFullName\":\"([\\s\\S]*?)\",");
    private static final Pattern Num = Pattern.compile("\"lActivityCount\":([\\s\\S]*?),");
    private static final Pattern ISLIVE = Pattern.compile("\"eLiveStatus\":([\\s\\S]*?),");
    private static List<String> qnString = new ArrayList<>();

    static {
//        qnString.add("OD");
        qnString.add("HD");
        qnString.add("SD");
        qnString.add("LD");
        qnString.add("FD");
    }
    /**
     * 搜索
     * @param keyWords  搜索关键字
     * @param isLive 是否搜索直播中的信息
     * @return
     */
    public static List<Owner> search(String keyWords, String isLive){
        List<Owner> list = new ArrayList<>();
        String url = "https://search.cdn.huya.com/?m=Search&do=getSearchContent&q=" + keyWords + "&uid=0&v=4&typ=-5&livestate=" + isLive + "&rows=5&start=0";
        String result = HttpUtil.doGet(url);
        JSONObject resultJsonObj = JSON.parseObject(result);
        if (result != null) {
            JSONArray ownerList = resultJsonObj.getJSONObject("response").getJSONObject("1").getJSONArray("docs");
            Iterator<Object> it = ownerList.iterator();
            while(it.hasNext()){
                JSONObject responseOwner = (JSONObject) it.next();
                Owner owner = new Owner();
                owner.setNickName(responseOwner.getString("game_nick"));
                owner.setCateName(responseOwner.getString("game_name"));
                owner.setHeadPic(responseOwner.getString("game_avatarUrl52"));
                owner.setPlatform("huya");
                owner.setRoomId(responseOwner.getString("room_id"));
                owner.setIsLive(responseOwner.getBoolean("gameLiveOn") ? "1" : "0");
                owner.setFollowers(responseOwner.getInteger("game_activityCount"));
                list.add(owner);
            }
        }
        return list;
    }

    /**
     * 获取真实地址
     * @param urls
     * @param roomId
     */
    public static void getRealUrl(Map<String, String> urls, String roomId) {
        String room_url = "https://m.huya.com/" + roomId;
        String response = HttpRequest.create(room_url)
                .setContentType(HttpContentType.FORM)
                .putHeader("User-Agent", "Mozilla/5.0 (Linux; Android 5.0; SM-G900P Build/LRX21T) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.100 Mobile Safari/537.36")
                .get().getBody();
        Matcher matcher = PATTERN.matcher(response);
        Matcher matcher2 = PATTERN2.matcher(response);
        if (!matcher2.find()){
            System.out.println("没提取到虎牙ayyuid");
        }
        if (!matcher.find()) {
            return;
        }
        String result = matcher.group();
        String result2 = matcher2.group();
        if (result.contains("replay")){
            return;
        }
        try{
            result = result.substring(result.indexOf("\":\"")+3, result.lastIndexOf("\""));
            result = new String(Base64.getDecoder().decode(result), "utf-8");
            String finalResult = result.replaceAll("(ratio=[^&]*)&", "").replaceAll("m3u8", "flv");
            List<Integer> qnList = getQns(roomId);
            result2 = result2.substring(result2.indexOf("\":")+2, result2.lastIndexOf(","));
            urls.put("ayyuid", result2);
            urls.put("OD", "http://tx.flv.huya.com" + finalResult.substring(finalResult.indexOf("/src")));
            for (int i = 0; i < qnList.size() ; i++) {
                int qn = qnList.get(i);
                if (qn != 0) {
                    urls.put(qnString.get(i), "http://tx.flv.huya.com" + finalResult.substring(finalResult.indexOf("/src")) + "&ratio=" + qnList.get(i));
                }
            }
        }catch (Exception e){
            return;
        }
    }

    /**
     * 获取房间所有清晰度
     * @param roomId
     * @return
     * @throws UnsupportedEncodingException
     */
    public static List<Integer> getQns(String roomId) throws UnsupportedEncodingException {
        HashMap<String,String> headerMap = new HashMap<>();
        headerMap.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headerMap.put("Accept-Encoding", "gzip");
        headerMap.put("Accept-Language", "zh-CN,zh;q=0.8");
        headerMap.put("Cache-Control", "max-age=0");
        headerMap.put("Connection", "keep-alive");
        headerMap.put("Host", "www.huya.com");
        headerMap.put("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36 SE 2.X MetaSr 1.0");

        String basicInfoUrl = String.format("https://www.huya.com/%s", roomId);
        String html = HttpRequest.getContent(basicInfoUrl, headerMap, null);

        Pattern pJson = Pattern.compile("var hyPlayerConfig *= *(.*?});");
        Matcher matcher = pJson.matcher(html);
        matcher.find();
        JSONObject obj = JSONObject.parseObject(matcher.group(1));
        String stream = obj.getString("stream");
        stream = new String(Base64.getDecoder().decode(stream), "UTF-8");
        obj = JSONObject.parseObject(stream);

        List<Integer> qnList = new ArrayList<>();
        JSONArray qns = obj.getJSONArray("vMultiStreamInfo");
        for(int i=0; i< qns.size(); i++) {
            int qn = qns.getJSONObject(i).getInteger("iBitRate");
            qnList.add(qn);
        }
        Collections.sort(qnList);
        Collections.reverse(qnList);
        return qnList;
    }

    /**
     * 刷新分类缓存
     * @return
     */
    public static void refreshArea(){
        List<List<AreaInfo>> areaMapTemp = new ArrayList<>();
        areaMapTemp.add(refreshSingleArea("1", "网游"));
        areaMapTemp.add(refreshSingleArea("2", "单机"));
        areaMapTemp.add(refreshSingleArea("3", "手游"));
        areaMapTemp.add(refreshSingleArea("8", "娱乐"));
        Global.platformAreaMap.put("huya",areaMapTemp);
    }

    /**
     * 获取虎牙单个类型下的所有分区
     * @param areaCode
     * @return
     */
    private static List<AreaInfo> refreshSingleArea(String areaCode, String typeName){
        String url = "https://m.huya.com/cache.php?m=Game&do=ajaxGameList&bussType=" + areaCode;
        List<AreaInfo> areaListTemp = new ArrayList<>();
        String result = HttpRequest.create(url)
                .setContentType(HttpContentType.FORM)
                .putHeader("User-Agent", "Mozilla/5.0 (Linux; Android 5.0; SM-G900P Build/LRX21T) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.100 Mobile Safari/537.36")
                .get().getBody();
        JSONObject resultJsonObj = JSON.parseObject(result);
        if (null != resultJsonObj) {
            JSONArray data = resultJsonObj.getJSONArray("gameList");
            Iterator<Object> it = data.iterator();
            while (it.hasNext()) {
                JSONObject areaInfo = (JSONObject) it.next();
                AreaInfo huyaArea = new AreaInfo();
                huyaArea.setAreaType(areaCode);
                huyaArea.setTypeName(typeName);
                huyaArea.setAreaId(areaInfo.getString("gid"));
                huyaArea.setAreaName(areaInfo.getString("gameFullName"));
                huyaArea.setAreaPic("https://huyaimg.msstatic.com/cdnimage/game/" + huyaArea.getAreaId() + "-MS.jpg");
                huyaArea.setPlatform("huya");
                areaListTemp.add(huyaArea);
                Global.HuyaCateMap.put(huyaArea.getAreaId(), huyaArea.getAreaName());
                if (!Global.AllAreaMap.containsKey(typeName)){
                    List<String> list = new ArrayList<>();
                    list.add(huyaArea.getAreaName());
                    Global.AreaTypeSortList.add(typeName);
                    Global.AreaInfoSortMap.put(typeName, list);
                }else {
                    if(!Global.AllAreaMap.get(typeName).containsKey(huyaArea.getAreaName())){
                        Global.AreaInfoSortMap.get(typeName).add(huyaArea.getAreaName());
                    }
                }
                Global.AllAreaMap.computeIfAbsent(typeName, k -> new HashMap<>())
                        .computeIfAbsent(huyaArea.getAreaName(), k -> new HashMap<>()).put("huya", huyaArea);
            }
        }
        return areaListTemp;
    }

    /**
     * 获取虎牙房间信息
     * @param roomId
     * @return
     */
    public static LiveRoomInfo getRoomInfoOld(String roomId) {
        String url = "https://search.cdn.huya.com/?m=Search&do=getSearchContent&q=" + roomId + "&uid=0&v=4&typ=-5&livestate=0&rows=5&start=0";
        String result = HttpUtil.doGet(url);
        JSONObject resultJsonObj = JSON.parseObject(result);
        LiveRoomInfo liveRoomInfo = new LiveRoomInfo();
        if (result != null) {
            JSONArray ownerList = resultJsonObj.getJSONObject("response").getJSONObject("1").getJSONArray("docs");
            Iterator<Object> it = ownerList.iterator();
            JSONObject responseOwner = (JSONObject) it.next();
            liveRoomInfo.setPlatForm("huya");
            liveRoomInfo.setRoomId(responseOwner.getString("room_id"));
            liveRoomInfo.setCategoryId(responseOwner.getString("cate_id"));//分类id不对
            liveRoomInfo.setCategoryName(responseOwner.getString("game_name"));
            liveRoomInfo.setRoomName(responseOwner.getString("live_intro"));
            liveRoomInfo.setOwnerName(responseOwner.getString("game_nick"));
            //liveRoomInfo.setRoomPic(responseOwner.getString("room_thumb"));
            liveRoomInfo.setOwnerHeadPic(responseOwner.getString("game_avatarUrl52"));
//            liveRoomInfo.setOnline(responseOwner.getInteger("game_activityCount"));//这个接口获取不到观看人数
            liveRoomInfo.setIsLive(responseOwner.getBoolean("gameLiveOn") ? 1 : 0);
        }
        return liveRoomInfo;
    }

    /**
     * 通过移动端请求获取虎牙房间信息
     * @param roomId
     * @return
     */
    public static LiveRoomInfo getRoomInfo(String roomId) {
        String room_url = "https://m.huya.com/" + roomId;
        String response = HttpRequest.create(room_url)
                .setContentType(HttpContentType.FORM)
                .putHeader("User-Agent", "Mozilla/5.0 (Linux; Android 5.0; SM-G900P Build/LRX21T) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.100 Mobile Safari/537.36")
                .get().getBody();
        Matcher matcherOwnerName = OwnerName.matcher(response);
        Matcher matcherRoomName = RoomName.matcher(response);
        Matcher matcherRoomPic = RoomPic.matcher(response);
        Matcher matcherOwnerPic = OwnerPic.matcher(response);
        Matcher matcherAREA = AREA.matcher(response);
        Matcher matcherNum = Num.matcher(response);
        Matcher matcherISLIVE = ISLIVE.matcher(response);
        if (!(matcherOwnerName.find() && matcherRoomName.find() && matcherRoomPic.find()
                && matcherOwnerPic.find() && matcherAREA.find() && matcherNum.find()
                && matcherISLIVE.find())){
            System.out.println("获取房间信息异常");
            return new LiveRoomInfo();
        }
        String resultOwnerName = matcherOwnerName.group();
        String resultRoomName = matcherRoomName.group();
        String resultRoomPic = matcherRoomPic.group();
        String resultOwnerPic = matcherOwnerPic.group();
        String resultAREA = matcherAREA.group();
        String resultNum = matcherNum.group();
        String resultISLIVE = matcherISLIVE.group();
        LiveRoomInfo liveRoomInfo = new LiveRoomInfo();

        liveRoomInfo.setRoomId(roomId);
        liveRoomInfo.setPlatForm("huya");
        liveRoomInfo.setOwnerName(getMatchResult(resultOwnerName, "\":\"", "\""));
        liveRoomInfo.setRoomName(getMatchResult(resultRoomName,"\":\"", "\""));
        liveRoomInfo.setRoomPic(getMatchResult(resultRoomPic, "\":\"", "\""));
        liveRoomInfo.setOwnerHeadPic(getMatchResult(resultOwnerPic, "\":\"", "\""));
        liveRoomInfo.setCategoryName(getMatchResult(resultAREA, "\":\"", "\""));
        if (!getMatchResult(resultNum, "\":", ",").equals("") ) {
            liveRoomInfo.setOnline(Integer.valueOf(getMatchResult(resultNum, "\":", ",")));
        } else {
            liveRoomInfo.setOnline(0);
        }
        liveRoomInfo.setIsLive(getMatchResult(resultISLIVE, "\":", ",").equals("2") ? 1 : 0);

        return liveRoomInfo;
    }

    /**
     * 根据分页获取推荐直播间
     * @param page 页数
     * @param size 每页大小
     * @return
     */
    public static List<LiveRoomInfo> getRecommend(int page, int size){
        List<LiveRoomInfo> list = new ArrayList<>();
        int realPage = page/6 + 1;
        int start = (page-1)*size%120;
        if (size == 10){
            realPage = page/12 + 1;
            start = (page-1)*size%120;
        }
        String url = "https://www.huya.com/cache.php?m=LiveList&do=getLiveListByPage&tagAll=0&page="+realPage;
        String result = HttpUtil.doGet(url);
        JSONObject resultJsonObj = JSON.parseObject(result);
        if (resultJsonObj.getInteger("status") == 200) {
            JSONArray data = resultJsonObj.getJSONObject("data").getJSONArray("datas");
            for (int i = start; i < start+size; i++){
                JSONObject roomInfo = data.getJSONObject(i);
                LiveRoomInfo liveRoomInfo = new LiveRoomInfo();
                liveRoomInfo.setPlatForm("huya");
                liveRoomInfo.setRoomId(roomInfo.getString("profileRoom"));
                liveRoomInfo.setCategoryId(roomInfo.getString("gid"));
                liveRoomInfo.setCategoryName(roomInfo.getString("gameFullName"));
                liveRoomInfo.setRoomName(roomInfo.getString("introduction"));
                liveRoomInfo.setOwnerName(roomInfo.getString("nick"));
                liveRoomInfo.setRoomPic(roomInfo.getString("screenshot"));
                liveRoomInfo.setOwnerHeadPic(roomInfo.getString("avatar180"));
                liveRoomInfo.setOnline(Integer.valueOf(roomInfo.getString("totalCount")));
                liveRoomInfo.setIsLive(1);
                list.add(liveRoomInfo);
            }
        }
        return list;
    }

    /**
     * 获取虎牙分区房间
     * @param area
     * @param page
     * @param size
     * @return
     */
    public static List<LiveRoomInfo> getAreaRoom(String area, int page, int size){
        List<LiveRoomInfo> list = new ArrayList<>();
        int realPage = page/6 + 1;
        int start = (page-1)*size%120;
        if (size == 10){
            realPage = page/12 + 1;
            start = (page-1)*size%120;
        }
        AreaInfo areaInfo = Global.getAreaInfo("huya", area);
        String url = "https://www.huya.com/cache.php?m=LiveList&do=getLiveListByPage&gameId=" + areaInfo.getAreaId() + "&tagAll=0&page="+realPage;
        String result = HttpUtil.doGet(url);
        JSONObject resultJsonObj = JSON.parseObject(result);
        if (resultJsonObj.getInteger("status") == 200) {
            JSONArray data = resultJsonObj.getJSONObject("data").getJSONArray("datas");
            for (int i = start; i < start+size; i++){
                JSONObject roomInfo = data.getJSONObject(i);
                LiveRoomInfo liveRoomInfo = new LiveRoomInfo();
                liveRoomInfo.setPlatForm("huya");
                liveRoomInfo.setRoomId(roomInfo.getString("profileRoom"));
                liveRoomInfo.setCategoryId(roomInfo.getString("gid"));
                liveRoomInfo.setCategoryName(roomInfo.getString("gameFullName"));
                liveRoomInfo.setRoomName(roomInfo.getString("introduction"));
                liveRoomInfo.setOwnerName(roomInfo.getString("nick"));
                liveRoomInfo.setRoomPic(roomInfo.getString("screenshot"));
                liveRoomInfo.setOwnerHeadPic(roomInfo.getString("avatar180"));
                liveRoomInfo.setOnline(Integer.valueOf(roomInfo.getString("totalCount")));
                liveRoomInfo.setIsLive(1);
                list.add(liveRoomInfo);
            }
        }
        return list;
    }

    /**
     * 分割搜索结果
     * @param str
     * @return
     */
    private static String getMatchResult(String str, String indexStartStr, String indexEndStr) {
        String result;
        result = str.substring(str.indexOf(indexStartStr)+indexStartStr.length(),str.lastIndexOf(indexEndStr));
        return result;
    }
}
