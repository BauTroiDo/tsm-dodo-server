local json = require 'cjson'

Setting = {}
local this = Setting;

this.tradeEnable = true;
this.expressEnable = true;

this.pkEnable = false;

this.client = {};

this.client.playOneEnable = true;
this.client.playerInfoMode = 1;
this.client.DisplayMode = 1;
this.client.npcInfoMode = 1;
this.client.uiStyle = "common0";

this.client.musicEnable = true;
this.client.musicVolume = 5;
this.client.musicMode = 1;

this.client.soundEnable = true;
this.client.soundVolume = 5;

this.client.chatStyle = 1;
this.client.currentChatState = 1;
this.client.dungeonUIDontAsk = false;
this.client.expBottleIDontAsk = false;

this.client.channelStyle = {};
this.client.channelStyle[EChannel.System] = 6;
this.client.channelStyle[EChannel.World] = 1;
this.client.channelStyle[EChannel.Scene] = 9;
this.client.channelStyle[EChannel.Whisper] = 0;
this.client.channelStyle[EChannel.GM] = 7;
this.client.channelStyle[EChannel.Team] = 4;
this.client.channelStyle[EChannel.Organization] = 2;
this.client.channelStyle[EChannel.Ally] = 5;
this.client.channelStyle[EChannel.Self] = 6;
this.client.channelStyle[EChannel.BattleRoyale] = 7;
this.client.channelStyle[EChannel.AI] = 8;

this.client.channelEnable = {};
this.client.channelEnable[EChannel.System] = true;
this.client.channelEnable[EChannel.World] = true;
this.client.channelEnable[EChannel.Scene] = true;
this.client.channelEnable[EChannel.Whisper] = true;
this.client.channelEnable[EChannel.GM] = true;
this.client.channelEnable[EChannel.Team] = true;
this.client.channelEnable[EChannel.Organization] = true;
this.client.channelEnable[EChannel.Ally] = true;
this.client.channelEnable[EChannel.Self] = true;
this.client.channelEnable[EChannel.BattleRoyale] = true;
this.client.channelEnable[EChannel.AI] = true;

this.client.lastServerId = nil;
this.client.lastAccount = "";
this.client.lastPassword = "";
this.client.lastLoginKind = 1; --ELogin.AccPwd
this.client.lastLoginToken = 0;

this.client.relationshipFavoriteWarriors = {};

function Setting.Initialize()
  local jsonText = CGResourceManager.ReadText("Setting.txt");

  if not string.IsNullOrEmpty(jsonText) then
    local data = json.decode(jsonText);

    if data.playOneEnable ~= nil then
      this.client.playOneEnable = data.playOneEnable;
    end

    if data.playerInfoMode ~= nil then
      this.client.playerInfoMode = data.playerInfoMode;
    end
  
    if data.DisplayMode ~= nil then
      this.client.DisplayMode = data.DisplayMode;
    end

    if data.npcInfoMode ~= nil then
      this.client.npcInfoMode = data.npcInfoMode;
    end

    if data.uiStyle ~= nil then
      this.client.uiStyle = data.uiStyle;
    end

    if data.musicEnable ~= nil then
      this.client.musicEnable = data.musicEnable;
    end

    if data.musicVolume ~= nil then
      this.client.musicVolume = data.musicVolume;
    end

    if data.musicMode ~= nil then
      this.client.musicMode = data.musicMode;
    end

    if data.soundEnable ~= nil then
      this.client.soundEnable = data.soundEnable;
    end

    if data.soundVolume ~= nil then
      this.client.soundVolume = data.soundVolume;
    end

    if data.chatStyle ~= nil then
      this.client.chatStyle = data.chatStyle;
    end
    
    if data.currentChatState ~= nil then
      this.client.currentChatState = data.currentChatState;
    end

    if data.channelStyle ~= nil then
      for k, v in pairs(data.channelStyle) do
        this.client.channelStyle[tonumber(k)] = v;
      end
    end

    if data.channelEnable ~= nil then
      for k, v in pairs(data.channelEnable) do
        this.client.channelEnable[tonumber(k)] = v;
      end
    end

    if data.lastServerId ~= nil then
      this.client.lastServerId = data.lastServerId;
    end

    if data.lastAccount ~= nil then
      this.client.lastAccount = data.lastAccount;
    end

    if data.lastPassword ~= nil then
      this.client.lastPassword = data.lastPassword;
    end
  
    if data.lastLoginKind ~= nil then
      this.client.lastLoginKind = data.lastLoginKind;
    end
  
    if data.lastLoginToken ~= nil then
      this.client.lastLoginToken = data.lastLoginToken;
    end

    if data.attSet ~= nil then
      this.client.attSet = data.attSet;
    end

    if data.dungeonUIDontAsk ~= nil then
      this.client.dungeonUIDontAsk = data.dungeonUIDontAsk;
    end

    if data.expBottleIDontAsk ~= nil then
      this.client.expBottleIDontAsk = data.expBottleIDontAsk;
    end

    if data.relationshipFavoriteWarriors ~= nil then
      this.client.relationshipFavoriteWarriors = data.relationshipFavoriteWarriors;
    end
  end
end

function Setting.CheckShowBody(role)
  if role.kind == EHuman.Players then
    if this.client.playerInfoMode == 2 then
    elseif this.client.playerInfoMode == 3 then
    elseif this.client.playerInfoMode == 4 then
    elseif this.client.playerInfoMode == 5 then
    end

    if MachineBox.client.general[EMachineBoxSwitch.EcoMode] and MachineBox.autoMove then
      return false;
    end

  elseif Contains(role.kind, EHuman.FollowNpc, EHuman.CartNpc) then
    if SceneManager.CheckLimit(SceneManager.sceneId, ESceneLimit.NoFollowNpc) then return false end

    if this.client.npcInfoMode ~= 1 then
      return false;
    end

    if MachineBox.client.general[EMachineBoxSwitch.EcoMode] and MachineBox.autoMove then
      return false;
    end
  end

  return true;
end

function Setting.CheckShowOrg(role)
  return this.client.playerInfoMode ~= 2;
end

function Setting.CheckShowHUD(role)
  if role.kind == EHuman.Players or role.kind == EHuman.Player then
    if this.client.playerInfoMode == 2 then
    elseif this.client.playerInfoMode == 3 then
      return false;
    elseif this.client.playerInfoMode == 4 then
      if Team.GetLeader(role.index) ~= Team.GetLeader(Role.playerId) then
        return false;
      end
    elseif this.client.playerInfoMode == 5 then
      if not Team.IsLeader(role.index) or role ~= Team.GetLeader(Role.playerId) then
        return false;
      end
    end
    
  elseif role.kind == EHuman.FollowNpc then
    if this.client.npcInfoMode ~= 1 then
      return false;
    end

    if DragonBoat.InRaceScene() then
      return false;
    end

    if HouseManager.IsInHouse() then
      return false;
    end

    if BattleRoyale.GetBattleRoyaleState() then
      return false;
    end

  elseif role.kind == EHuman.CartNpc then
    return false;
  end

  return true;
end

function Setting.Save()
  local jsonText = json.encode(this.client);

  CGResourceManager.WriteText("Setting.txt", jsonText);
end
