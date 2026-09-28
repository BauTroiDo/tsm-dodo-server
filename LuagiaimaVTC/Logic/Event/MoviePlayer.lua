--region MoviePlayer.lua
--[[
    功能: 演出物件播放器，播放MovieObject檔案。
]]
require "Logic/Event/MovieSound"
require "Logic/Event/MovieObjectManager"
require "Common/Queue"

EMovieState = {
  Idle = 1,
  Loading = 2,
  Ready = 3,
  Play = 4,
  Pause = 5,
  End = 6,
  TestPlay = 7
};

MoviePlayer = {};
local this = MoviePlayer;

--const
local MOVIE_SCENE_ORDER = 3000;
local MOVIE_SCENE_INVISIBLE_ORDER = -3000;
local MinMoveDist = 20;

this.poseInterval = { 0.3, 0.23, 0.1 };
this.speedTable = { 0.08, 0.12, 0.4 };
this.timeScale = 1000;

--var
this.freeze = false;
this.state = EMovieState.Idle;
this.isTalking = false;
local currentMovieObject = nil;
local waitPlayQueue = Queue.New();
local isEfficacy1 = false;
local IsEfficacy2 = false;
local IsEfficacy3 = false;
this.IsShowBg = true;
this.SceneEffect = 0;
local nowPoint = 0;
local totalPoint = 0;
local Delta_Count = 5;

local movieStateFunc = {};

function MoviePlayer.Destroy()
  poolMgr:DestroyPool("BaseSty");
end

function MoviePlayer.Initialize()
  --[[movieBackGround.gameObject = GameObject.Find("MovieBackGround");
  movieBackGround.transform = movieBackGround.gameObject.transform;
  movieBackGround.rectTransform = movieBackGround.gameObject:GetComponent("RectTransform");
  movieBackGround.rawImage = movieBackGround.gameObject:GetComponent("RawImage");
  movieBackGround.canvas = movieBackGround.gameObject:GetComponent("Canvas");
  movieBackGround.center = {};
  movieBackGround.center.x = 0;
  movieBackGround.center.y = 0;]]
  CGResourceManager.Load(
    "BaseSty",
    "",
    function(assetName, asset)
      poolMgr:CreatePool("BaseSty", asset);
    end
  );
end

local SortingOrder = function()
  local ground = currentMovieObject.ground;
  local player = currentMovieObject.pactor;

  if ground ~= nil then 
    ground:SortingOrder(MOVIE_SCENE_ORDER);
  end

  if player ~= nil then
    if Between(nowPoint, player.startStep + 1, player.startStep + player.totalStep) then
      player:SortingOrder(MOVIE_SCENE_ORDER);
    else
      player:SortingOrder(MOVIE_SCENE_INVISIBLE_ORDER);
    end
  end

  for k, npc in pairs(currentMovieObject.nactor) do
    if Between(nowPoint, npc.startStep + 1, npc.startStep + npc.totalStep) then
      npc:SortingOrder(MOVIE_SCENE_ORDER);
    else
      npc:SortingOrder(MOVIE_SCENE_INVISIBLE_ORDER);
    end
  end

  for k, pic in pairs(currentMovieObject.pic) do
    if Between(nowPoint, pic.startStep + 1, pic.startStep + pic.totalStep) then
      pic:SortingOrder(MOVIE_SCENE_ORDER);
    else
      pic:SortingOrder(MOVIE_SCENE_INVISIBLE_ORDER);
    end
  end
end

local LoadMovie = function(movieObject)
  currentMovieObject = movieObject;

  if currentMovieObject == nil then return end

  local ground = currentMovieObject.ground;
  local player = currentMovieObject.pactor;

  Tlog("MoviePlayer", "Start loading movie source");
  this.state = EMovieState.Loading;

  if ground ~= nil then
    ground:Load();
  end

  for k, pic in pairs(currentMovieObject.pic) do
    pic:Load(ground.movieBackGround, k);
  end

  if player ~= nil then
    player:Load(ground.movieBackGround, RoleController.Copy(Role.player));    
  end

  for k, npc in pairs(currentMovieObject.nactor) do
    npc:Load(ground.movieBackGround, Role.CreateNpc(k, npc.npcId, Vector2.New(npc.destXY[0].x, npc.destXY[0].y)));
  end
  
  SortingOrder();

  Tlog("MoviePlayer", "loading movie source Finish");
  this.state = EMovieState.Ready;
end

function MoviePlayer.PlayMovie(movieID)
  log(string.Concat("Prepare to play movie: ", movieID));
  if this.state ~= EMovieState.Idle and this.state ~= EMovieState.End then
    log("Add Wait Play Movie");
    waitPlayQueue:Enqueue(movieID);
    return;
  end

  this.ClearMovie();
  UI.HideAll(true, "UICheck");
  MovieObjectManager.GetMovie(tostring(movieID), LoadMovie);
end

function MoviePlayer.ClearMovie()    
  if Role.player ~= nil then
    Scene.UpdateCameraSize(MapManager.mapWidth, MapManager.mapHeight);
    Scene.UpdateCamera(Role.player.position);
  end

  if currentMovieObject == nil then return end
  
  if currentMovieObject.ground ~= nil then
    currentMovieObject.ground:Destroy();
    currentMovieObject.ground = nil;
  end

  if currentMovieObject.pactor ~= nil then
    currentMovieObject.pactor:Destroy();
    currentMovieObject.pactor = nil;
  end
  
  for k in pairs(currentMovieObject.nactor) do
    currentMovieObject.nactor[k]:Destroy();
    currentMovieObject.nactor[k] = nil;
  end
  table.Clear(currentMovieObject.nactor);
  currentMovieObject.nactor = nil;

  for k in pairs(currentMovieObject.pic) do
    currentMovieObject.pic[k]:Destroy();
    currentMovieObject.pic[k] = nil;
  end
  table.Clear(currentMovieObject.pic);
  currentMovieObject.pic = nil;
  
  for k in pairs(currentMovieObject.talk) do
    currentMovieObject.talk[k]:Destroy();
    currentMovieObject.talk[k] = nil;
  end
  table.Clear(currentMovieObject.talk);
  currentMovieObject.talk = nil;

  currentMovieObject.crunNode = nil;
  currentMovieObject.efficacy = nil;
  currentMovieObject = nil;  
  UI.HideAll(false); 
end

local InDistance = function(dist, x1, y1, x2, y2)
  return math.pow(dist, 2) > math.pow((x2 - x1), 2) + math.pow((y2 - y1), 2);
end

function MoviePlayer.Update()
  if movieStateFunc[this.state] ~= nil then
    movieStateFunc[this.state]();
  end
end

function MoviePlayer.IsPlaying()
  return this.state ~= EMovieState.Idle;
end

local Eff1Update = function()
  local ground = currentMovieObject.ground;
  local efficacy = currentMovieObject.efficacy;

  if efficacy.Eff1[nowPoint] == 0 then
    ground.alpha = 255;
  elseif efficacy.Eff1[nowPoint] == 1 then
    if ground.alpha == 255 then
      isEfficacy1 = false;
    end

    ground.alpha = ground.alpha + Delta_Count;
    if ground.alpha >= 255 then
      ground.alpha = 255;
    end

    currentMovieObject.crunNode.overTime = CGTimer.time;
    ground.oldTime = CGTimer.time;

    if currentMovieObject.pactor ~= nil then
      currentMovieObject.pactor.oldTime = CGTimer.time;
    end

    for k, pic in pairs(currentMovieObject.pic) do
      pic.oldTime = CGTimer.time;
    end

    for k, npc in pairs(currentMovieObject.nactor) do
      npc.oldTime = CGTimer.time;
    end

  elseif efficacy.Eff1[nowPoint] == 2 then
    if ground.alpha == 0 then
      isEfficacy1 = false;
    end
    ground.alpha = ground.alpha-Delta_Count;
    if ground.alpha <=0 then
      ground.alpha = 0;
    end
    currentMovieObject.crunNode.overTime = CGTimer.time;
    ground.oldTime = CGTimer.time;
    if currentMovieObject.pactor ~= nil then
      currentMovieObject.pactor.oldTime = CGTimer.time;
    end
    
    for k, pic in pairs(currentMovieObject.pic) do
      pic.oldTime = CGTimer.time;
    end

    for k, npc in pairs(currentMovieObject.nactor) do
      npc.oldTime = CGTimer.time;
    end
      
  elseif efficacy.Eff1[nowPoint] == 3 then
  elseif efficacy.Eff1[nowPoint] == 4 then
  elseif efficacy.Eff1[nowPoint] == 5 then
  end
end

local Eff2Update = function()
  local ground = currentMovieObject.ground;
  local efficacy = currentMovieObject.efficacy;

  if (efficacy.Eff2[nowPoint] == 0) then
    ground.quakeOffset.x = 0;
    ground.quakeOffset.y = 0;    
  elseif (efficacy.Eff2[nowPoint] == 1) then
    if ground.quakeStep  == 0 then
      ground.quakeOffset.x = 5;
      ground.quakeOffset.y = -5;
      ground.quakeStep = 1;
    elseif ground.quakeStep == 1 then
      ground.quakeOffset.x = -5;
      ground.quakeOffset.y = 5;
      ground.quakeStep = 2;
    elseif ground.quakeStep == 2 then
      ground.quakeOffset.x = 5;
      ground.quakeOffset.y = 2;
      ground.quakeStep = 3;
    elseif ground.quakeStep == 3 then
      ground.quakeOffset.x = -5;
      ground.quakeOffset.y = -5;
      ground.quakeStep = 4;
    elseif ground.quakeStep == 4 then
      ground.quakeOffset.x = 0;
      ground.quakeOffset.y = 0;
      ground.quakeStep = 5;
    elseif ground.quakeStep == 5 then
      ground.quakeOffset.x = 5;
      ground.quakeOffset.y = 5;
      ground.quakeStep = 6;
    elseif ground.quakeStep == 6 then
      ground.quakeOffset.x = 0;
      ground.quakeOffset.y = -2;
      ground.quakeStep = 0;
    end
  elseif efficacy.Eff2[nowPoint] == 2 then
    if efficacy.IsOverTime(ground.quakeInterval, 2) then
      ground.quakeInterval = ground.quakeInterval-8;
      if ground.quakeInterval <=0 then
        ground.quakeInterval = 0;
      end
      if ground.quakeStep == 0 then
        if ground.quakeInterval >= 0 then 
          ground.quakeOffset.x = 2;
          ground.quakeOffset.y = -2;
          ground.quakeStep = 1;
        else
          ground.quakeOffset.x = 5;
          ground.quakeOffset.y = -5;
          ground.quakeStep = 1;
        end
      elseif ground.quakeStep == 1 then
        if ground.quakeInterval >= 0 then 
          ground.quakeOffset.x = -2;
          ground.quakeOffset.y = 2;
          ground.quakeStep = 2;
        else
          ground.quakeOffset.x = -5;
          ground.quakeOffset.y = 5;
          ground.quakeStep = 2;
        end
      elseif ground.quakeStep == 2 then
        if ground.quakeInterval >= 0 then 
          ground.quakeOffset.x = 3;
          ground.quakeOffset.y = 0;
          ground.quakeStep = 3;
        else
          ground.quakeOffset.x = 5;
          ground.quakeOffset.y = 2;
          ground.quakeStep = 3;
        end
      elseif ground.quakeStep == 3 then
        if ground.quakeInterval >= 0 then 
          ground.quakeOffset.x = -2;
          ground.quakeOffset.y = -2;
          ground.quakeStep = 4;
        else
          ground.quakeOffset.x = -5;
          ground.quakeOffset.y = -5;
          ground.quakeStep = 4;
        end
      elseif ground.quakeStep == 4 then
        if ground.quakeInterval >= 0 then 
          ground.quakeOffset.x = 0;
          ground.quakeOffset.y = 0;
          ground.quakeStep = 5;
        else
          ground.quakeOffset.x = 0;
          ground.quakeOffset.y = 0;
          ground.quakeStep = 5;
        end
      elseif ground.quakeStep == 5 then
        if ground.quakeInterval >= 0 then 
          ground.quakeOffset.x = 5;
          ground.quakeOffset.y = 5;
          ground.quakeStep = 6;
        else
          ground.quakeOffset.x = 2;
          ground.quakeOffset.y = 2;
          ground.quakeStep = 6;
        end
      elseif ground.quakeStep == 6 then
        if ground.quakeInterval >= 0 then 
          ground.quakeOffset.x = 0;
          ground.quakeOffset.y = 0;
          ground.quakeStep = 0;
        else
          ground.quakeOffset.x = 0;
          ground.quakeOffset.y = -2;
          ground.quakeStep = 0;
        end
      end
    end
  elseif (efficacy.Eff2[nowPoint] == 3) then --底圖不秀    
    this.IsShowBG = false;    
  elseif (efficacy.Eff2[nowPoint] == 4) then --漸漸放大         
    if efficacy.IsOverTime(30,2) then
      --Form1.zoomIn;     
    end
  elseif (efficacy.Eff2[nowPoint] == 5) then --畫面復原    
    if efficacy.IsOverTime(30,2) then
      --Form1.zoomOut;    
    end
  elseif (efficacy.Eff2[nowPoint] == 6) then --直接放大    
    --[[if Form1.zoomInType<>9 then
      InflateRect(Form1.SourceRC,-200,-150);
          Form1.zoomInType:=9;
    end]]         
  end
end

local Eff3Update = function()
  local ground = currentMovieObject.ground;
  local efficacy = currentMovieObject.efficacy;

  if (efficacy.Eff3[nowPoint] == 0) then
    ground.quakeOffset.x = 0;
    ground.quakeOffset.y = 0;    
  elseif (efficacy.Eff3[nowPoint] == 1) then
    if ground.quakeStep == 0 then
      ground.quakeOffset.x = 5;
      ground.quakeOffset.y = -5;
      ground.quakeStep = 1;
    elseif ground.quakeStep == 1 then
      ground.quakeOffset.x = -5;
      ground.quakeOffset.y = 5;
      ground.quakeStep = 2;
    elseif ground.quakeStep == 2 then
      ground.quakeOffset.x = 5;
      ground.quakeOffset.y = 2;
      ground.quakeStep = 3;
    elseif ground.quakeStep == 3 then
      ground.quakeOffset.x = -5;
      ground.quakeOffset.y = -5;
      ground.quakeStep = 4;
    elseif ground.quakeStep == 4 then
      ground.quakeOffset.x = 0;
      ground.quakeOffset.y = 0;
      ground.quakeStep = 5;
    elseif ground.quakeStep == 5 then
      ground.quakeOffset.x = 5;
      ground.quakeOffset.y = 5;
      ground.quakeStep = 6;
    elseif ground.quakeStep == 6 then
      ground.quakeOffset.x = 0;
      ground.quakeOffset.y = -2;
      ground.quakeStep = 0;
    end
  elseif (efficacy.Eff3[nowPoint] == 2) then
    if efficacy.IsOverTime(quakeInterval, 2) then
      ground.quakeInterval = ground.quakeInterval-8;
      if ground.quakeInterval <=0 then
        ground.quakeInterval = 0;
      end
      if ground.quakeStep == 0 then
        if ground.quakeInterval >= 0 then
          ground.quakeOffset.x = 2;
          ground.quakeOffset.y = -2;
          ground.quakeStep = 1;
        else
          ground.quakeOffset.x = 5;
          ground.quakeOffset.y = -5;
          ground.quakeStep = 1;
        end
      elseif ground.quakeStep == 1 then
        if ground.quakeInterval >= 0 then
          ground.quakeOffset.x = -2;
          ground.quakeOffset.y = 2;
          ground.quakeStep = 2;
        else
          ground.quakeOffset.x = 5;
          ground.quakeOffset.y = 5;
          ground.quakeStep = 2;
        end
      elseif ground.quakeStep == 2 then
        if ground.quakeInterval >= 0 then
          ground.quakeOffset.x = 3;
          ground.quakeOffset.y = 0;
          ground.quakeStep = 3;
        else
          ground.quakeOffset.x = 5;
          ground.quakeOffset.y = 2;
          ground.quakeStep = 3;
        end
      elseif ground.quakeStep == 3 then
        if ground.quakeInterval >= 0 then
          ground.quakeOffset.x = -2;
          ground.quakeOffset.y = -2;
          ground.quakeStep = 4;
        else
          ground.quakeOffset.x = -5;
          ground.quakeOffset.y = -5;
          ground.quakeStep = 4;
        end
      elseif ground.quakeStep == 4 then
        if ground.quakeInterval >= 0 then
          ground.quakeOffset.x = 0;
          ground.quakeOffset.y = 0;
          ground.quakeStep = 5;
        else
          ground.quakeOffset.x = 0;
          ground.quakeOffset.y = 0;
          ground.quakeStep = 5;
        end
      elseif ground.quakeStep == 5 then
        if ground.quakeInterval >= 0 then
          ground.quakeOffset.x = 5;
          ground.quakeOffset.y = 5;
          ground.quakeStep = 6;
        else
          ground.quakeOffset.x = 2;
          ground.quakeOffset.y = 2;
          ground.quakeStep = 6;
        end
      elseif ground.quakeStep == 6 then
        if ground.quakeInterval >= 0 then
          ground.quakeOffset.x = 0;
          ground.quakeOffset.y = 0;
          ground.quakeStep = 0;
        else
          ground.quakeOffset.x = 0;
          ground.quakeOffset.y = -2;
          ground.quakeStep = 0;
        end
      end
    end
  elseif (efficacy.Eff3[nowPoint] == 3) then--底圖不秀    
    this.IsShowBG = false;    
  elseif (efficacy.Eff3[nowPoint] == 4) then--漸漸放大    
    if efficacy.IsOverTime(30,3) then
      --Form1.zoomIn;
    end
  elseif (efficacy.Eff3[nowPoint] == 5) then--畫面復原    
    if efficacy.IsOverTime(30,3) then
      --Form1.zoomOut;     
    end
  elseif (efficacy.Eff3[nowPoint] == 6) then--直接放大    
    --[[if Form1.zoomInType<>9 then 
      InflateRect(Form1.SourceRC,-200,-150);
      Form1.zoomInType:=9;        
    end]]
  end
end

local UpdateMovie = function()
  local ground = currentMovieObject.ground;
  local player = currentMovieObject.pactor;
  local efficacy = currentMovieObject.efficacy;

  if nowPoint > totalPoint then
    if player ~= nil then
      Scene.MovieUpdateCamera(player.position, ground.width, ground.height);
    end

    this.freeze = true;
    this.state = EMovieState.End;
    TlogError("UpdateMovie", string.Concat("MovieEnd: ", nowPoint));
    return;
  end

  --特效
  if isEfficacy1 then
    Eff1Update();
    if isEfficacy1 then
      return;
    end
  end

  if IsEfficacy2 then
    Eff2Update();
  end

  if IsEfficacy3 then
    Eff3Update();
  end

  --對話
  if this.isTalking then
     for k, talk in pairs(currentMovieObject.talk) do
      if nowPoint == talk.point then
        --TlogError("Tft_Talk", string.format("talktime: %s", tostring(talk.time)));
        if not talk:IsOverTime() then
          for kk, pic in pairs(currentMovieObject.pic) do
            if pic:IsOverTime() then
              pic.frame = pic.frame + 1;                               
            end

            if pic.frame > pic.h * pic.w then
              pic.frame = 1;
            end
          end

          return;
        else
          this.isTalking = false; --對話狀態設為沒有對話中
        end
      end
    end
  end

  --更新位置
  local allArrived = true;

  if ground ~= nil then
    if Between(nowPoint, ground.startStep, ground.startStep + ground.totalStep) then
      if nowPoint == ground.startStep and ground.destXY[1] ~= nil then
        ground.destination.x = ground.destXY[1].x;
        ground.destination.y = ground.destXY[1].y;
      end

      if not ground:UpdatePosition() then
        allArrived = false;
      end
    end
  end

  if player ~= nil then
    if Between(nowPoint, player.startStep, player.startStep + player.totalStep) then
      if not player:UpdatePosition() then
        allArrived = false;
      end
    end
  end

  for k, npc in pairs(currentMovieObject.nactor) do
    if Between(nowPoint, npc.startStep, npc.startStep + npc.totalStep) then
      if not npc:UpdatePosition() then
        allArrived = false;
      end
    end
  end

  for k, pic in pairs(currentMovieObject.pic) do
    if Between(nowPoint, pic.startStep, pic.startStep + pic.totalStep) then
      if not pic:UpdatePosition() then
        allArrived = false;
      end
    end
  end

  if not currentMovieObject.crunNode:IsOverTime(currentMovieObject.crunNode.time[nowPoint]) then
    allArrived = false;
  end

  --全部到點後進到下一步
  if allArrived then
    --結束原本對話
    UI.Close(UICheck);

    for k, talk in pairs(currentMovieObject.talk) do
      if (nowPoint > 0) and (nowPoint == talk.point) then
        if not this.isTalking and not talk.isOver then
          if talk.npcId == 65535 then
            talk:Show(Role.player, this.timeScale);
          else
            for kk, npc in pairs(currentMovieObject.nactor) do
              if npc.npcId == talk.npcId then
                talk:Show(npc.roleController, this.timeScale);
                break;
              end
            end
          end
          this.isTalking = true;
          return;
        end
      end
    end
    
    if ground ~= nil then
      if Between(nowPoint, ground.startStep, ground.startStep + ground.totalStep) then
        ground:NextStep();
      end
    end
    
    if player ~= nil then
      if Between(nowPoint, player.startStep, player.startStep + player.totalStep) then
        player:NextStep();
      end
    end
    
    for k, npc in pairs(currentMovieObject.nactor) do
      if Between(nowPoint, npc.startStep, npc.startStep + npc.totalStep) then
        npc:NextStep();
      end
    end
    
    for k, pic in pairs(currentMovieObject.pic) do
      if Between(nowPoint, pic.startStep, pic.startStep + pic.totalStep) then
        pic:NextStep();
      end
    end

    nowPoint = nowPoint + 1;
    Tlog("NextStep", string.format("nowPoint: %s", tostring(nowPoint)));
    if nowPoint <= totalPoint then
      if efficacy.Eff1[nowPoint] == 0 then
        --不調整
        ground.alpha = 255;
        isEfficacy1 = false;
        SceneEffect.CloseEffect();
      elseif efficacy.Eff1[nowPoint] == 1 then
        --由暗到亮
        ground.alpha = 0;
        isEfficacy1 = true;
      elseif efficacy.Eff1[nowPoint] == 2 then
        --由亮到暗
        ground.alpha = 255;
        isEfficacy1 = true;
      elseif efficacy.Eff1[nowPoint] == 3 then
        --落葉
        SceneEffect.OpenEffect(ESceneEffect.Leaf);
        isEfficacy1 = false;
      elseif efficacy.Eff1[nowPoint] == 4 then
        --飄雪
        SceneEffect.OpenEffect(ESceneEffect.Snow);
        isEfficacy1 = false;
      elseif efficacy.Eff1[nowPoint] == 5 then
        --暴雨
        SceneEffect.OpenEffect(ESceneEffect.Rain);        
        isEfficacy1 = false;
      elseif efficacy.Eff1[nowPoint] == 6 then
        --下雨 
        SceneEffect.OpenEffect(ESceneEffect.OnlyRain);        
        isEfficacy1 = false;
      end
      
      if efficacy.Eff2[nowPoint] == 0 then
        ground.quakeOffset.x = 0;
        ground.quakeOffset.y = 0;
        IsEfficacy2 = false;
      elseif efficacy.Eff2[nowPoint] == 1 then
        ground.quakeOffset.x = 0;
        ground.quakeOffset.y = 0;
        IsEfficacy2 = true;
      elseif efficacy.Eff2[nowPoint] == 2 then
        ground.quakeOffset.x = 0;
        ground.quakeOffset.y = 0;
        IsEfficacy2 = true;
        ground.quakeInterval = 180;
      elseif efficacy.Eff2[nowPoint] == 3 then
        IsEfficacy2 = true;
      elseif efficacy.Eff2[nowPoint] == 4 then
        IsEfficacy2 = true;
      elseif efficacy.Eff2[nowPoint] == 5 then
        IsEfficacy2 = true;
      elseif efficacy.Eff2[nowPoint] == 6 then
        IsEfficacy2 = true;
      end

      if efficacy.Eff3[nowPoint] == 0 then
        ground.quakeOffset.x = 0;
        ground.quakeOffset.y = 0;
        IsEfficacy3 = false;
      elseif efficacy.Eff3[nowPoint] == 1 then
        ground.quakeOffset.x = 0;
        ground.quakeOffset.y = 0;
        IsEfficacy3 = true;
      elseif efficacy.Eff3[nowPoint] == 2 then
        ground.quakeOffset.x = 0;
        ground.quakeOffset.y = 0;
        IsEfficacy3 = true;
        ground.quakeInterval = 180;
      elseif efficacy.Eff3[nowPoint] == 3 then
        IsEfficacy3 = true;
      elseif efficacy.Eff3[nowPoint] == 4 then
        IsEfficacy3 = true;
      elseif efficacy.Eff3[nowPoint] == 5 then
        IsEfficacy3 = true;
      elseif efficacy.Eff3[nowPoint] == 6 then
        IsEfficacy2 = true;
      end

      if efficacy.Eff4[nowPoint] > 0 then
        --if (SoundMode=Sound_Mode1) or (efficacy.Eff4[nowPoint]<=105) then //mp3格式模式二不襏
          --JK_sound_bus_back_music_play(AppPath+Movie_Sound[efficacy.Eff4[nowPoint]);
      end

      currentMovieObject.crunNode.overTime = CGTimer.time;
      efficacy.OverTime1 = CGTimer.time;
      efficacy.OverTime2 = CGTimer.time;
      efficacy.OverTime3 = CGTimer.time;
      efficacy.OverTime4 = CGTimer.time;
    end
  end
end

local ShowMovie = function()
  local player = currentMovieObject.pactor;
  local ground = currentMovieObject.ground;

  if ground ~= nil then
    ground:Update();
  end

  if player ~= nil then
    if Between(nowPoint, player.startStep + 1, player.startStep + player.totalStep) then
      if player.destXY[player.nowStep].residual == EResidual.Residual1 or player.destXY[player.nowStep].residual == EResidual.Residual2 or player.destXY[player.nowStep].residual == EResidual.Residual3 then
        player:ShowResidual(2, player.destXY[player.nowStep].residual);
      end
      
      if player.destXY[player.nowStep].residual == EResidual.Overturn or player.destXY[player.nowStep].residual == EResidual.Huge or player.destXY[player.nowStep].residual == EResidual.Small then
        player:Show(false, 0, player.destXY[player.nowStep].residual);
      else
        player:Show(false, 0, 0);
      end
    end
  end

  for k, npc in pairs(currentMovieObject.nactor) do
    if Between(nowPoint, npc.startStep + 1, npc.startStep + npc.totalStep) then
      if npc.destXY[npc.nowStep].residual == EResidual.Residual1 or npc.destXY[npc.nowStep].residual == EResidual.Residual2 or npc.destXY[npc.nowStep].residual == EResidual.Residual3 then
        npc:ShowResidual(2, npc.destXY[npc.nowStep].residual);
      end
      
      if npc.destXY[npc.nowStep].residual == EResidual.Role_Overturn or npc.destXY[npc.nowStep].residual == EResidual.Huge or npc.destXY[npc.nowStep].residual == EResidual.Small then
        npc:Show(false, 0, npc.destXY[npc.nowStep].residual);
      else
        npc:Show(false, 0, 0);
      end
    end
  end
  
  for k, pic in pairs(currentMovieObject.pic) do
    if Between(nowPoint, pic.startStep + 1, pic.startStep + pic.totalStep) then
      pic:Show();
    end
  end
end

movieStateFunc[EMovieState.Idle] = nil;

movieStateFunc[EMovieState.Loading] = function()  
end

movieStateFunc[EMovieState.Ready] = function()
  logError("Movie Start !!");

  nowPoint = 0;
  totalPoint = currentMovieObject.totalPoint;

  this.state = EMovieState.Play;
end

movieStateFunc[EMovieState.Pause] = function()
  local player = currentMovieObject.pactor;

  if CheckActorsArrived(currentPoint) then 
    state = EMovieState.Play;    
    movieSeq:Play(); 
  end

  if player ~= nil and moviePlayerActor ~= nil then
    if Between(currentPoint, player.startStep, player.startStep + player.totalStep) and moviePlayerActor ~= nil then
      moviePlayerActor:UpdateShow(false);
    end
  end

  if currentMovieObject.nactor ~= nil and movieNpcActor ~= nil and nactorActStep ~= nil then
    for k, npc in pairs(currentMovieObject.nactor) do
      local npcRole = movieNpcActor[npc.npcId][k];
      local npcActStep = nactorActStep[npc.npcId][k];
      if Between(currentPoint, npc.startStep, npc.startStep + npc.totalStep) and npcRole ~= nil and npcActStep ~= nil then
        local nowStep = currentPoint - npc.startStep;
        local time = this.poseInterval[npc.destXY[nowStep].speedLevel];
        local maxFrame = npcRole:GetAnimationFrame(npc.destXY[nowStep].pose);
        if maxFrame > 0 then 
          if npc.destXY[nowStep].frame == 0 then
            npcActStep = npcActStep + 1;
            if npc.destXY[nowStep].frame >= maxFrame then
              npcActStep = 0;
            end
          else
            if npc.destXY[nowStep].frame > maxFrame then
              npcActStep = maxFrame - 1;
            else
              npcActStep = npc.destXY[nowStep].frame - 1;
            end
          end
          npcRole:SetAnimationForceFrame(npcActStep);            
        end
      end
      npcRole:UpdateShow(false);
    end
  end
end

movieStateFunc[EMovieState.End] = function()          
  if waitPlayQueue.Count > 0 then 
    Tlog("MoviePlayer", "PlayNextMovie");
    this.PlayMovie(waitPlayQueue:Dequeue());    
  end
  
  if this.freeze then 
    if EventManager.IsRunning then
      EventManager.SetConduct(true);
      return;
    end

    return;
  end
end

movieStateFunc[EMovieState.Play] = function()
  UpdateMovie();
  
  if this.state == EMovieState.End then return end
  
  SortingOrder();
  ShowMovie();
end

function MoviePlayer.GetPosition(position, destination, speedLevel, deltaTime)
  local speed = this.speedTable[speedLevel] or this.speedTable[2];
  local moveDist = math.round(deltaTime * speed * this.timeScale);

  local vector = destination - position;
  if vector.sqrMagnitude <= math.pow(moveDist, 2) then
    position.x = destination.x;
    position.y = destination.y;

    return position;
  end

  position = position + (vector.normalized * moveDist);

  return position;
end