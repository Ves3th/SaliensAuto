package com.gmail.vkhanh234.SaliensAuto;

import com.gmail.vkhanh234.SaliensAuto.data.PlayerInfo.PlayerInfo;
import com.gmail.vkhanh234.SaliensAuto.data.PlayerInfo.PlayerInfoResponse;
import com.gmail.vkhanh234.SaliensAuto.data.ReportScore.ReportScore;
import com.gmail.vkhanh234.SaliensAuto.data.ReportScore.ReportScoreResponse;
import com.gmail.vkhanh234.SaliensAuto.data.Planet.*;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import org.fusesource.jansi.AnsiConsole;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;


public class Main {
    public  static String token;
    public static String currentPlanet;
    public static Zone currentZone;
    public static int planetSearchMode = 0;

    public static CheckVersionThread versionThread;
    public static ProcessThread thread;

    public static boolean pause=true;
    public static boolean noHighDiff=true;

    public static int vcCounter=5;
    public static void main(String[] args){
        AnsiConsole.systemInstall();

        checkVersion();

        debug(highlight("SaliensAuto "+ VersionUtils.getLocalVersion(),Color.PURPLE_BRIGHT));
        debug(Color.RED_BRIGHT+"Please keep checking "+Color.GREEN_BRIGHT+"https://github.com/KickVN/SaliensAuto"+Color.RED_BRIGHT
                +" regularly in case there is a new update"+Color.RESET);


        if(args.length>=1) setToken(args[0]);
        if(args.length>=2) setPlanetSearchMode(Integer.valueOf(args[1]));
        if(args.length>=3) start();

        sendHelp();
        Scanner scanner = new Scanner(System.in);
        while(true){
            String s = scanner.nextLine();
            if(s.length()==0) continue;
            String[] spl = s.split(" ");
            onCommand(spl[0],spl);
        }
    }

    private static void onCommand(String s, String[] args) {
        if(s.equalsIgnoreCase("settoken") && args.length>=2){
            setToken(args[1]);
        }
        else if(s.equalsIgnoreCase("setsearchmode") && args.length>=2){
            setPlanetSearchMode(Integer.parseInt(args[1]));
        }
        else if(s.equalsIgnoreCase("start")){
            start();
        }
        else if(s.equalsIgnoreCase("stop")){
            stop();
        }
        else if(s.equalsIgnoreCase("exit")){
            System.exit(0);
        }
        else{
            sendHelp();
        }
    }

    public static void setPlanetSearchMode(int v) {
        planetSearchMode = v;
        debug(highlight("Planet Search Mode")+" has been set to "+highlight(planetSearchMode+""));
    }

    private static void setToken(String s) {
        token = s;
        debug(highlight("Token")+" has been set to "+highlight(token));
    }

    private static void sendHelp() {
        debug(
                highlight("Commands List: \n",Color.CYAN_BRIGHT)
                +"\t "+highlight("settoken <token>")+" - Set your token. Visit https://steamcommunity.com/saliengame/gettoken to get your token\n"
                +"\t "+highlight("setsearchmode 0")+" - (Default mode) Choose planet with the highest captured rate to have a chance of winning games and finish off planets to let new one bloom\n"
                +"\t "+highlight("setsearchmode 1")+" - Choose planet with the highest difficulties to get more XP\n"
                +"\t "+highlight("start")+" - Start automating\n"
                +"\t "+highlight("stop")+" - Stop automating\n"
                +"\t "+highlight("exit")+" - What can this do? Idk. Figure it out by yourself."
        );
    }

    private static void stop() {
        if(thread!=null && !thread.isInterrupted()) {
            pause=true;
            thread.interrupt();
        }
        debug(highlight("Stopping...",Color.RED_BRIGHT));
    }

    public static void start(){
        stop();
        pause=false;
        debug("Starting with token "+highlight(token)+" and search mode "+highlight(planetSearchMode+"")+"...");
        thread = new ProcessThread();
        thread.start();

    }

    private static String highlight(String s) {
        return highlight(s,Color.YELLOW_BRIGHT);
    }
    private static String highlight(String s,String color) {
        return color+s+Color.RESET;
    }

    private static void progress() {
        if(currentPlanet==null) {
            debug(highlight("No planet found",Color.RED_BRIGHT));
            return;
        }
        else {
            debug("Attempting to progress in planet " + highlight(currentPlanet));
            joinPlanet();
        }
        while(!pause) {
            currentZone = getAvailableZone();
            if (currentZone == null) {
                debug(highlight("No zone found",Color.RED_BRIGHT));
                return;
            }
            if (!joinZone()) {
                debug(highlight("Failed joining zone " + highlight(currentZone.zone_position+""),Color.RED_BRIGHT));
                return;
            }
            try {
                debug("Wait 110s");
                checkVersion();
                Thread.sleep(50000);
                debug("Wait 60s");
                Thread.sleep(30000);
                debug("Wait 30s");
                Thread.sleep(15000);
                debug("Wait 15s");
                Thread.sleep(5000);
                debug("Wait 10s");
                Thread.sleep(5000);
                debug("Wait 5s");
                Thread.sleep(5000);
                if(!reportScore()){
                    debug(highlight("Failed completing the instance",Color.RED_BRIGHT));
                }
                leaveCurrentGame();
                debug(highlight("===================================",Color.GREEN_BRIGHT));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static boolean reportScore(){
        int score = getZoneScore();
        debug("Attempting to complete an instance with a score of "+highlight(score+"")
                +" in zone "+highlight(currentZone.zone_position+"")+"(difficulty "+highlight(currentZone.difficulty+"")+")");
        String data = RequestUtils.post("ITerritoryControlMinigameService/ReportScore","score="+score+"&language=english");
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<ReportScoreResponse> jsonAdapter = moshi.adapter(ReportScoreResponse.class);
        try {
            ReportScoreResponse res = jsonAdapter.fromJson(data);
            if(res!=null && res.response!=null){
                ReportScore response = res.response;
                if(response==null || response.new_score==null) return false;
                debug(highlight("Completed an instance. You have reached level "+highlight(response.new_level+"")
                        +" ("+highlight(response.new_score)+"/"+highlight(response.next_level_score)+" ~ "
                        +highlight(ProgressUtils.getPercent(Integer.valueOf(response.new_score),Integer.valueOf(response.next_level_score))+"")+"%)",Color.CYAN_BRIGHT));
                int scoreLeft = Integer.valueOf(response.next_level_score)-Integer.valueOf(response.new_score);
                debug(highlight("At this rate, to reach next level, you need to wait at least ",Color.CYAN_BRIGHT)+highlight(ProgressUtils.getTimeLeft(scoreLeft,getPointPerSec(currentZone.difficulty))));
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static int getZoneScore() {
        int score=getPointPerSec(currentZone.difficulty);
        return score*120;
    }

    private static int getPointPerSec(int difficulty) {
        switch (currentZone.difficulty){
            case 1: return 5;
            case 2: return 10;
            case 3: return 20;
        }
        return 0;
    }

    private static boolean joinZone() {
        debug("Attempting to join zone "+highlight(currentZone.zone_position+"")+" (difficulty "+highlight(currentZone.difficulty+"")+")");
        String data = RequestUtils.post("ITerritoryControlMinigameService/JoinZone","zone_position="+currentZone.zone_position);
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<ZoneInfoResponse> jsonAdapter = moshi.adapter(ZoneInfoResponse.class);
        try {
            ZoneInfoResponse response = jsonAdapter.fromJson(data);
            if(response==null || response.response==null || response.response.zone_info==null || response.response.zone_info.captured) return false;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void joinPlanet() {
        RequestUtils.post("ITerritoryControlMinigameService/JoinPlanet","id="+currentPlanet);
    }


    public static void leaveCurrentGame(){
        PlayerInfo info = getPlayerInfo();
        if(info.active_zone_game!=null){
            RequestUtils.post("IMiniGameService/LeaveGame","gameid="+info.active_zone_game);
            debug(highlight("Left game "+highlight(info.active_zone_game),Color.CYAN_BRIGHT));
        }
        if(info.active_planet!=null) currentPlanet = info.active_planet;
    }

    public static void leaveCurrentPlanet(){
        PlayerInfo info = getPlayerInfo();
        if(info.active_planet!=null){
            RequestUtils.post("IMiniGameService/LeaveGame","gameid="+info.active_planet);
            debug(highlight("Left planet "+highlight(info.active_planet),Color.CYAN_BRIGHT));
        }
        currentPlanet = getAvailablePlanet();
    }

    public static PlayerInfo getPlayerInfo(){
        String dat = RequestUtils.post("ITerritoryControlMinigameService/GetPlayerInfo","");
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PlayerInfoResponse> jsonAdapter = moshi.adapter(PlayerInfoResponse.class);
        try {
            PlayerInfoResponse response = jsonAdapter.fromJson(dat);
            if(response==null || response.response==null) return null;
            return response.response;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getAvailablePlanet()
    {
        debug(highlight("Searching for planet...",Color.CYAN_BRIGHT));
        Planets planets = getPlanets();
        if(planets==null) return null;
        if(planetSearchMode==0) return getTopPriorityPlanet(planets);
        else return getMostXpPlanet(planets);
    }
    public static String getTopPriorityPlanet(Planets planets){
        int min = Integer.MAX_VALUE;
        String id="1";
        for(Planet planet:planets.planets){
            if(planet.state==null || !planet.state.active || planet.state.captured) continue;
            debug("- Planet "+highlight(planet.id)+"("+highlight(planet.state.name)+")'s priority is "+highlight(planet.state.priority+""));
            if(min>planet.state.priority){
                min = planet.state.priority;
                id=planet.id;
            }
        }
        debug(highlight("=> Choose planet "+highlight(id),Color.GREEN_BRIGHT));
        return id;
    }

    public static String getMostXpPlanet(Planets planets){
        noHighDiff=true;
        int[] max = new int[4];
        String id = "1";
        for(Planet planet:planets.planets){
            Planet planetData = getPlanetData(planet.id);
            int[] difficuties = planetData.getDifficulties();
            debug("- Planet "+highlight(planet.id)+"("+highlight(planet.state.name)+") has "+highlight(difficuties[1]+"",Color.GREEN_BRIGHT)
                    +" low, "+highlight(difficuties[2]+"",Color.CYAN_BRIGHT)+" medium and "+highlight(difficuties[3]+"",Color.RED_BRIGHT)+" high");
            if(difficuties[3]>0) noHighDiff=false;
            for(int i=3;i>=1;i--){
                if(max[i]<difficuties[i]){
                    max=difficuties;
                    id=planet.id;
                    break;
                }
                else if(max[i]>difficuties[i]) break;
            }
        }
        debug(highlight("=> Choose planet "+highlight(id),Color.GREEN_BRIGHT));
        return id;
    }

    public static Zone getAvailableZone(){
        Planet planet = getPlanetData(currentPlanet);
        if(planet==null) return null;
        Zone zone = planet.getAvailableZone();
        if(planetSearchMode==1 && zone.difficulty<3 && !noHighDiff) return null;
        else {
            noHighDiff=false;
            return zone;
        }
    }

    public static Planet getPlanetData(String id){
        String data = RequestUtils.get("GetPlanet","id="+id);
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PlanetsResponse> jsonAdapter = moshi.adapter(PlanetsResponse.class);
        try {
            PlanetsResponse response = jsonAdapter.fromJson(data);
            if(response==null || response.response==null || response.response.planets==null || response.response.planets.size()==0) return null;
            return response.response.planets.get(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Planets getPlanets(){
        String res = RequestUtils.get( "GetPlanets", "active_only=1" );
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PlanetsResponse> jsonAdapter = moshi.adapter(PlanetsResponse.class);
        try {
            PlanetsResponse planets = jsonAdapter.fromJson(res);
            if(planets==null || planets.response==null || planets.response.planets==null || planets.response.planets.size()==0) return null;
            return planets.response;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

//    public static void changeClan(String id){
//        PlayerInfo info = getPlayerInfo();
//        if(info.clan_info==null || !String.valueOf(info.clan_info.accountid).equalsIgnoreCase(id)){
//
//        }
//    }

    public static void compareVersion(){
        String remoteVer = VersionUtils.getRemoteVersion();
        String localVer = VersionUtils.getLocalVersion();
        if(remoteVer.equalsIgnoreCase(localVer)) return;
        debug(highlight("=================================",Color.RED_BRIGHT));
        debug(highlight("There is a new version available: ",Color.GREEN_BRIGHT)+highlight("SaliensAuto "+remoteVer));
        debug(highlight("Your current version: ",Color.GREEN_BRIGHT)+highlight("SaliensAuto "+localVer));
        debug(highlight("Go here and download latest version: ",Color.GREEN_BRIGHT)+highlight("https://github.com/KickVN/SaliensAuto/releases",Color.CYAN_BRIGHT));
        debug(highlight("=================================",Color.RED_BRIGHT));
    }

    public static void checkVersion(){
        //Only check every 5 zones
        if(vcCounter<5){
            vcCounter++;
            return;
        }
        vcCounter=0;

        if(versionThread!=null && !versionThread.isInterrupted()) versionThread.interrupt();
        versionThread = new CheckVersionThread();
        versionThread.start();
    }

    public static void debug(String s){
        System.out.println("["+new SimpleDateFormat("HH:mm:ss").format(new Date())+"] "+s);
    }

    private static class ProcessThread extends Thread {
        @Override
        public void run() {
            while(!pause) {
                try {
                    leaveCurrentGame();
                    leaveCurrentPlanet();
                    progress();
                }catch (Exception e){}
            }
            debug(highlight("Automation stopped",Color.RED_BRIGHT));
        }
    }
    private static class CheckVersionThread extends Thread {
        @Override
        public void run() {
            compareVersion();
        }
    }
}
