
EShadow = {
  None = 0,
  Human = 1,
  Horse = 2,
  Monster = 3,
}

ETextureSize = {
  high = 2000,
  middle = 100,
  low = 20,
}

UniversalJmp = {};
local this = UniversalJmp;

local shadowMaterial = nil;

local normalShadowJmp = nil;
local normalShadowTexture = nil;
local normalShadowRects = nil;

local horseShadowJmp = nil;
local horseShadowTexture = nil;
local horseShadowRects = nil;

local monsterShadowJmp = nil;
local monsterShadowTexture = nil;
local monsterShadowRects = nil;

--戰鬥中顯示動圖
local fightJmp = nil;
local fightTexture = nil;
local fightRects = nil;

--海上顯示動圖
local shipJmp = nil;
local shipTexture = nil;
local shipRects = nil;

--攻城戰
local cityPlayers = {};
local cityWeapons = {};

function UniversalJmp.Initialize()
  UISetting.SetDisplay();
  
  CGResourceManager.Load(
    "ShadowMaterial",
    "",
    function(assetName, asset)
      shadowMaterial = asset;
    end
  );

  --超大影子
  TextureManager.GetPngSingleTexture("MonsterShadow",
    function(texture)
      if texture ~= nil then
        monsterShadowJmp = Jmp.New(ERolePart.Shadow, texture, 1);

        JmpAtlasMaker.GenerateAtlas(
          monsterShadowJmp,
          monsterShadowJmp.colorTable,
          function(width, height, format, rawData, atlasRects)
            monsterShadowTexture = TexturePool.Get(monsterShadowTexture, width, height, format);
            monsterShadowTexture:LoadRawTextureData(rawData);
            monsterShadowTexture:Apply();

            monsterShadowRects = atlasRects;
          end
        );
      end
    end
  );

  --座騎影子
  TextureManager.GetPngSingleTexture("HorseShadow",
    function(texture)
      if texture ~= nil then
        horseShadowJmp = Jmp.New(ERolePart.Shadow, texture, 8);

        JmpAtlasMaker.GenerateAtlas(
          horseShadowJmp,
          horseShadowJmp.colorTable,
          function(width, height, format, rawData, atlasRects)
            horseShadowTexture = TexturePool.Get(horseShadowTexture, width, height, format);
            horseShadowTexture:LoadRawTextureData(rawData);
            horseShadowTexture:Apply();

            horseShadowRects = atlasRects;
          end
        );
      end
    end
  );
  
  --人物影子
  TextureManager.GetPngSingleTexture("HumanShadow",
    function(texture)
      if texture ~= nil then
        normalShadowJmp = Jmp.New(ERolePart.Shadow, texture, 1);

        JmpAtlasMaker.GenerateAtlas(
          normalShadowJmp,
          normalShadowJmp.colorTable,
          function(width, height, format, rawData, atlasRects)
            normalShadowTexture = TexturePool.Get(normalShadowTexture, width, height, format);
            normalShadowTexture:LoadRawTextureData(rawData);
            normalShadowTexture:Apply();

            normalShadowRects = atlasRects;
          end
        );
      end
    end
  );

  --戰鬥中圖
  fightJmp = RolePicManager.GetJMP(ERolePart.Body, 1020);
  JmpAtlasMaker.GenerateAtlas(
    fightJmp,
    fightJmp.colorTable,
    function(width, height, format, rawData, atlasRects)
      fightTexture = TexturePool.Get(fightTexture, width, height, format);
      fightTexture:LoadRawTextureData(rawData);
      fightTexture:Apply();

      fightRects = atlasRects;
    end
  );
  
  --船圖
  shipJmp = RolePicManager.GetJMP(ERolePart.Body, 1016);
  JmpAtlasMaker.GenerateAtlas(
    shipJmp,
    shipJmp.colorTable,
    function(width, height, format, rawData, atlasRects)
      shipTexture = TexturePool.Get(shipTexture, width, height, format);
      shipTexture:LoadRawTextureData(rawData);
      shipTexture:Apply();

      shipRects = atlasRects;
    end
  );

  if LuaHelper.IsEditor and not LuaHelper.IsMacOS then return end
  if not Resource.HaveDownloadResource() then return end
  
  --攻城戰玩家
  for k, camp in pairs(ECamp) do
    cityPlayers[camp] = {};

    local cityPlayerNpcData = npcDatas[City.playerCampNpcId[camp]];
    cityPlayers[camp].jmp = RolePicManager.GetJMP(ERolePart.Body, cityPlayerNpcData.picId, ERolePose.Stand, ERolePose.Walk);

    local cityPlayerColor = TSColor.Copy(cityPlayers[camp].jmp.colorTable, cityPlayerColor);
    for k, colorPart in pairs(EColorPart) do
      cityPlayerColor = TSColor.Tint(cityPlayerColor, cityPlayers[camp].jmp.colorTable, colorPart, cityPlayerNpcData:GetColor(colorPart));
    end

    JmpAtlasMaker.GenerateAtlas(
      cityPlayers[camp].jmp,
      cityPlayerColor,
      function(width, height, format, rawData, atlasRects)
        cityPlayers[camp].texture = TexturePool.Get(cityPlayers[camp].texture, width, height, format);
        cityPlayers[camp].texture:LoadRawTextureData(rawData);
        cityPlayers[camp].texture:Apply();
  
        cityPlayers[camp].rects = atlasRects;
      end,
      ERolePose.Stand,
      ERolePose.Walk
    );
  end

  --攻城戰器械
  for k, weaponNpcId in pairs(City.weaponNpcId) do
    cityWeapons[weaponNpcId] = {};

    local weaponNpcData = npcDatas[weaponNpcId];
    cityWeapons[weaponNpcId].jmp = RolePicManager.GetJMP(ERolePart.Body, weaponNpcData.picId, ERolePose.Stand, ERolePose.Walk, ERolePose.Lie);

    local attackWeaponColor = TSColor.Copy(cityWeapons[weaponNpcId].jmp.colorTable, attackWeaponColor);
    local defendWeaponColor = TSColor.Copy(cityWeapons[weaponNpcId].jmp.colorTable, defendWeaponColor);
    for k, colorPart in pairs(EColorPart) do
      if colorPart == EColorPart.Helm and Contains(weaponNpcId, 37002, 37003, 37004, 37005, 37006, 37017, 37018, 37019, 37020, 37021) then
        attackWeaponColor = TSColor.Tint(attackWeaponColor, cityWeapons[weaponNpcId].jmp.colorTable, colorPart, 9, 1, 0);
      else
        attackWeaponColor = TSColor.Tint(attackWeaponColor, cityWeapons[weaponNpcId].jmp.colorTable, colorPart, weaponNpcData:GetColor(colorPart));
      end
      defendWeaponColor = TSColor.Tint(defendWeaponColor, cityWeapons[weaponNpcId].jmp.colorTable, colorPart, weaponNpcData:GetColor(colorPart));
    end

    cityWeapons[weaponNpcId].texture = {};

    JmpAtlasMaker.GenerateAtlas(
      cityWeapons[weaponNpcId].jmp,
      attackWeaponColor,
      function(width, height, format, rawData, atlasRects)
        cityWeapons[weaponNpcId].texture[ECamp.Attack] = TexturePool.Get(cityWeapons[weaponNpcId].texture[ECamp.Attack], width, height, format);
        cityWeapons[weaponNpcId].texture[ECamp.Attack]:LoadRawTextureData(rawData);
        cityWeapons[weaponNpcId].texture[ECamp.Attack]:Apply();
  
        cityWeapons[weaponNpcId].rects = atlasRects;
      end,
      ERolePose.Stand,
      ERolePose.Walk,
      ERolePose.Lie
    );

    JmpAtlasMaker.GenerateAtlas(
      cityWeapons[weaponNpcId].jmp,
      defendWeaponColor,
      function(width, height, format, rawData, atlasRects)
        cityWeapons[weaponNpcId].texture[ECamp.Defend] = TexturePool.Get(cityWeapons[weaponNpcId].texture[ECamp.Defend], width, height, format);
        cityWeapons[weaponNpcId].texture[ECamp.Defend]:LoadRawTextureData(rawData);
        cityWeapons[weaponNpcId].texture[ECamp.Defend]:Apply();
      end,
      ERolePose.Stand,
      ERolePose.Walk,
      ERolePose.Lie
    );
  end
end

--shadow
function UniversalJmp.GetShadow(kind)
  if kind == EShadow.Human then
    return normalShadowTexture, normalShadowJmp.width, normalShadowJmp.height, normalShadowRects;
  elseif kind == EShadow.Horse then
    return horseShadowTexture, horseShadowJmp.width, horseShadowJmp.height, horseShadowRects;
  elseif kind == EShadow.Monster then
    return monsterShadowTexture, monsterShadowJmp.width, monsterShadowJmp.height, monsterShadowRects;
  else
    return nil, 0, 0, nil;
  end
end

function UniversalJmp.GetShadowAni(kind, direction, inBattle)
  if kind == EShadow.Human then
    return 0, 0;
  elseif kind == EShadow.Horse then
    if inBattle then
      if direction == 0 then
        return 1, 0;
      else
        return 5, 0;
      end
    else
      return direction, 0;
    end
  elseif kind == EShadow.Monster then
    return 0, 0;
  else
    return 0, 0;
  end
end

function UniversalJmp.GetShadowMatirial()
  return shadowMaterial;
end

function UniversalJmp.GetShadowTexture(kind)
  if kind == EShadow.Human then
    return normalShadowTexture;
  elseif kind == EShadow.Horse then
    return horseShadowTexture;
  elseif kind == EShadow.Monster then
    return monsterShadowTexture;
  else
    return nil;
  end
end

function UniversalJmp.GetShadowOffsetY(kind)
  if kind == EShadow.Human then
    return 32;
  elseif kind == EShadow.Horse then
    return 32;
  elseif kind == EShadow.Monster then
    return 0;
  else
    return 0;
  end
end

--fight
function UniversalJmp.GetFight()
  return fightTexture, fightJmp.width, fightJmp.height, fightRects;
end

function UniversalJmp.GetFightTexture()
  return fightTexture;
end

--ship
function UniversalJmp.GetShip()
  return shipTexture, shipJmp.width, shipJmp.height, shipRects;
end

function UniversalJmp.GetShipTexture()
  return shipTexture;
end

--City
function UniversalJmp.GetCityPlayer(camp)
  return cityPlayers[camp].texture, cityPlayers[camp].jmp.width, cityPlayers[camp].jmp.height, cityPlayers[camp].rects;
end

function UniversalJmp.GetCityWeapon(npcId, camp)
  return cityWeapons[npcId].texture[camp], cityWeapons[npcId].jmp.width, cityWeapons[npcId].jmp.height, cityWeapons[npcId].rects;
end