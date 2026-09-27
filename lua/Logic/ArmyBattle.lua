ArmyBattleStatus = {
  None = 0,
  SignUp = 1,
  Prepare = 2,
  Attack = 3,
  Result = 4,
  Finish = 5,
  Rest = 6
}

ArmyAwardCon = {
  Win = 1,
  Lose = 2,
  Board = 3,
  Points = 4
}

ArmyBattleResult = {
  Win = 0,
  Lose = 1,
  Draw = 2,
  WrongState = 3,
  Unknow = 99
}

ArmyBattle = {}
local this = ArmyBattle;

this.battleStatus = -1;
this.battleStatusTime = 0;

this.DEFENCE_LIFE = 3;
this.MAX_DRUM_COUNT = 5;
this.MAX_ORDER_COUNT = 4;
this.MAX_GOLD_ORDER_COUNT = 1;
this.MAX_NPCFIGHT_COUNT = 2;

this.nowDrumCount = 4;
this.nowChoosePoint = 0;
this.nowChooseTeam = 1;

this.playerMoralColor = ArmyMoralColor.Red;
this.playerAttackCount = 3;
this.playerAttackCountTime = 0;
this.playerScore = 0;
this.playerDefencePoint = 0;
this.playerOrderCount = 0;
this.playerRank = 1;
this.playerResult = ArmyBattleResult.WrongState;

this.cityInfo = {};
this.teamInfos = {};
this.npcList = {};
this.defenceTeamInfos = {};
this.usedNpc = {};
this.award = {};

function ArmyBattle.Initialize()
  for i = 1, table.Count(strongholdDatas) do
    this.cityInfo[i] = {};
    this.cityInfo[i] = strongholdDatas[i];
    this.cityInfo[i].nowCount = this.cityInfo[i].count;
    this.cityInfo[i].nowTotalLife = 0;
    this.cityInfo[i].drumCount = 0;
    this.cityInfo[i].isFighting = false;
    this.cityInfo[i].isFight = {};
    this.cityInfo[i].isNpc = {};
    this.cityInfo[i].nowLife = {};

    for j = 1, this.cityInfo[i].count do
      this.cityInfo[i].isFight[j] = false;
      this.cityInfo[i].isNpc[j] = false;
      this.cityInfo[i].nowLife[j] = 3;
      this.cityInfo[i].nowTotalLife = this.cityInfo[i].nowTotalLife + this.cityInfo[i].nowLife[j];
    end

    this.cityInfo[i].rebuild = 0;
    this.cityInfo[i].playerInfo = {};
    this.cityInfo[i].npcList = {};
    this.cityInfo[i].idList = {};
    for j = 1, this.cityInfo[i].count do
      this.cityInfo[i].idList[j] = 0;
      this.cityInfo[i].npcList[j] = {};
      this.ResetDefenceList(i, j);
      this.cityInfo[i].playerInfo[j] = {};
    end
  end

  for i = 1, 2 do
    this.teamInfos[i] = {};
    this.teamInfos[i].id = 0;
    this.teamInfos[i].score = 0;
    this.teamInfos[i].orderCount = this.MAX_ORDER_COUNT + this.MAX_GOLD_ORDER_COUNT;
    this.teamInfos[i].orders = {};

    for j = 1, 5 do
      this.teamInfos[i].orders[j] = 0;
    end
  end

  this.ResetDefenceTeamInfos();

  this.award[ArmyAwardCon.Win] = {};
  this.award[ArmyAwardCon.Lose] = {};
  this.award[ArmyAwardCon.Board] = {};
  this.award[ArmyAwardCon.Points] = {};

  for _, v in pairs(strongholdAwardDatas) do
    table.insert(this.award[v.condition], v);
  end
end

function ArmyBattle.CheckRedDot()
  if Contains(this.battleStatus, ArmyBattleStatus.Prepare, ArmyBattleStatus.Attack) then
    local count = 0;
    for i = 1, DataFight.maxTeamNpc do
      if ArmyBattle.defenceTeamInfos[i] ~= nil and ArmyBattle.defenceTeamInfos[i].npcId ~= 0 then
        RedDot.Remove(ERedDot.ArmyBattleTeam);
        break;
      else
        count = count + 1;
      end
    end

    if count == DataFight.maxTeamNpc then
      RedDot.Add(ERedDot.ArmyBattleTeam);
    end
  end
end

function ArmyBattle.ResetDefenceList(cityIndex, teamIndex)
  local type = this.cityInfo[cityIndex]:GetTypeByScore();
  table.Clear(this.cityInfo[cityIndex].npcList[teamIndex]);

  for i = 1, 5 do
    table.insert(this.cityInfo[cityIndex].npcList[teamIndex], strongholdNPCDatas[type].npcList[i]);
  end

  this.cityInfo[cityIndex].drumCount = strongholdNPCDatas[type].count;
end

function ArmyBattle.ResetDefenceTeamInfos()
  for i = 0, DataFight.maxTeamNpc do
    this.defenceTeamInfos[i] = {};
    this.defenceTeamInfos[i].npcId = 0;
    this.defenceTeamInfos[i].level = 0;
    this.defenceTeamInfos[i].hp = 0;
    this.defenceTeamInfos[i].extraSkills = nil;
    this.defenceTeamInfos[i].skills = {};
    for j = 1, DataFight.maxTeamSkill do
      this.defenceTeamInfos[i].skills[j] = 0;
    end
  end
end

function ArmyBattle.SetPlayerData(data)
  this.playerAttackCount = data:ReadByte();
  this.playerAttackCountTime = DateTime.FromOADate(data:ReadDouble());
  this.playerScore = data:ReadUInt16();
  this.playerDefencePoint = data:ReadByte();

  local count = data:ReadByte();

  table.Clear(this.usedNpc);

  for i = 1, count do
    this.usedNpc[i] = {};
    this.usedNpc[i].npcId = data:ReadUInt16();
    this.usedNpc[i].time = data:ReadByte();
  end

  this.CheckRedDot();

  if UI.IsVisible(UIArmyBattle) then
    UIArmyBattle.UpdateUI();
  else
    UI.Open(UIArmyBattle);
  end
end

function ArmyBattle.SetBattleData(data)
  if this.battleStatus == -1 then
    this.Initialize();
  end

  this.battleStatusTime = DateTime.FromOADate(data:ReadDouble());
  this.battleStatus = data:ReadByte();
  local battleId = data:ReadByte();
  local count = data:ReadByte();

  for i = 1, count do
    this.teamInfos[i].id = data:ReadInt32();
    this.teamInfos[i].name = data:ReadString(data:ReadByte());
    this.teamInfos[i].score = data:ReadUInt16();
    this.teamInfos[i].orderCount = data:ReadByte();

    if this.teamInfos[i].id == Organization.Id then
      this.playerMoralColor = i;
    end

    table.Clear(this.teamInfos[i].orders);

    for j = 1, this.teamInfos[i].orderCount do
      this.teamInfos[i].orders[j] =  data:ReadByte();
    end
  end

  count = data:ReadByte();
  for i = 1, count do
    this.cityInfo[i].id = data:ReadByte();
    this.cityInfo[i].moral = data:ReadByte();
    this.cityInfo[i].nowCount = data:ReadByte();
    this.cityInfo[i].nowTotalLife = data:ReadByte();

    if data:ReadByte() == 0 then
      this.cityInfo[i].isFighting = false;
    else
      this.cityInfo[i].isFighting = true;
    end

    local time = data:ReadDouble();
    if DateTime.Compare(DateTime.FromOADate(time), CGTimer.serverTime) > 0 then
      this.cityInfo[i].rebuild = DateTime.FromOADate(time);
    else
      this.cityInfo[i].rebuild = 0;
    end
  end

  --C:171-001 <取得玩家資訊>
  Network.Send(171, 1);
end

function ArmyBattle.SetDefenceTeam(index, npcId, level, hp, extraSkills, skills)
  this.defenceTeamInfos[index].npcId = npcId;
  this.defenceTeamInfos[index].level = level;
  this.defenceTeamInfos[index].hp = hp;
  this.defenceTeamInfos[index].extraSkills = extraSkills;
  this.defenceTeamInfos[index].skills = skills;
end

function ArmyBattle.GetNpcList(npcInfos)
  table.Clear(this.npcList);
  this.npcList = npcInfos;

  if UI.IsVisible(UIArmyBattleFunction) then
    UIArmyBattleFunction.UpdateNpc();
  end
end

function ArmyBattle.GetDefAtkString(moralColor)
  if this.IsEnemy(moralColor) or this.IsDrumPoint(moralColor) then
    return string.Get(24364);
  else
    return string.Get(24243);
  end
end

function ArmyBattle.GetTotalCount(moral)
  local total = 0;
  for i = 1, table.Count(this.cityInfo) do
    if this.cityInfo[i].moral == moral then
      total = total + this.cityInfo[i].score;
    end
  end

  return total;
end

function ArmyBattle.GetStatusString()
  if this.battleStatus == ArmyBattleStatus.Prepare then
    return string.Get(24237);
  elseif this.battleStatus == ArmyBattleStatus.Attack then
    return string.Get(24258);
  elseif this.battleStatus == ArmyBattleStatus.Rest then
    return string.Get(24290);
  elseif this.battleStatus == ArmyBattleStatus.Result then
    return string.Get(24291);
  end

  return "Error";
end

function ArmyBattle.GetTotalAtkCount()
  if this.battleStatus == ArmyBattleStatus.Attack then
    return 3;
  else
    return 0;
  end
end

function ArmyBattle.GetPointCount(moral)
  local count = 0;

  for _, v in pairs(this.cityInfo) do
    if v.moral == moral then
      count = count + 1;
    end
  end

  return count;
end

function ArmyBattle.SliderValue(index)
  return this.cityInfo[index].nowTotalLife / this.GetTotalLife(index);
end

function ArmyBattle.GetTotalLife(index)
  local total = 0;

  for i = 1, table.Count(this.cityInfo[index].nowLife) do
    total = total + this.DEFENCE_LIFE;
  end

  return total;
end

function ArmyBattle.GetGoldOrderCount(orders)
  for _, v in pairs(orders) do
    if strongholdWarDatas[v.id].sellId ~= 0 and v.use == 0 then
      return 1;
    end
  end

  return 0;
end

function ArmyBattle.IsChiefOrSub()
  return Organization.IsChief(Role.playerId) or Organization.IsSubChief(Role.playerId);
end

function ArmyBattle.GetScore(star, turn, level)
  if turn >=3 then level = level - Role.playerMaxLv end;
  return 20 + 0.1 * 20 * 1 * star * (19 * turn ^ 2 + math.floor(0.1 * level));
end

function ArmyBattle.CheckTime(time)
  return time ~= 0 and DateTime.Compare(time, CGTimer.serverTime) > 0;
end

function ArmyBattle.ResetRebuildTime(index)
  this.cityInfo[index].rebuild = 0;
end

function ArmyBattle.LineRepeat(pointsCheck, points)
  if table.Count(pointsCheck) == 0 then return false end

  for _, v in pairs(pointsCheck) do
    if v[1][1] == points[2][1] and v[1][2] == points[2][2] and
       v[2][1] == points[1][1] and v[2][2] == points[1][2] then
      return true;
    end
  end

  return false;
end

function ArmyBattle.IsRoadIgnore(team)
  for i = 1, this.teamInfos[team].orderCount do
    if this.teamInfos[team].orders[i] ~= 0 then
      if strongholdWarDatas[this.teamInfos[team].orders[i]].orderType == 1 then
        return true;
      end
    end
  end

  return false;
end

function ArmyBattle.GetHPSprite(moral)
  if moral == ArmyMoralColor.Red then
    return UI.GetSprite("com001");
  elseif moral == ArmyMoralColor.Blue then
    return UI.GetSprite("com002");
  elseif moral == ArmyMoralColor.Yellow then
    return UI.GetSprite("com111");
  end
end

function ArmyBattle.GetFlagSprite(moral)
  if moral == ArmyMoralColor.Red then
    return UI.GetSprite("icon181");
  elseif moral == ArmyMoralColor.Blue then
    return UI.GetSprite("icon180");
  elseif moral == ArmyMoralColor.Yellow then
    return UI.GetSprite("icon207");
  end
end

function ArmyBattle.GetOutlineColor(moral)
  if moral == ArmyMoralColor.Red then
    return LuaHelper.GetColor(164, 47, 50, 255);
  elseif moral == ArmyMoralColor.Blue then
    return LuaHelper.GetColor(71, 119, 173, 255);
  end
end

function ArmyBattle.GetOrderText(data)
  if data.kind == 1 then
    if data.life == this.GetTotalLife(data.id) then
      if data.moral == ArmyMoralColor.Red then
        return string.Get(24285);
      elseif data.moral == ArmyMoralColor.Blue then
        return string.Get(24286);
      end
    else
      if data.moral == ArmyMoralColor.Red then
        return string.Get(24376);
      elseif data.moral == ArmyMoralColor.Blue then
        return string.Get(24377);
      end
    end
  elseif data.kind == 2 then
    if data.moral == ArmyMoralColor.Red then
      return string.Get(24288);
    elseif data.moral == ArmyMoralColor.Blue then
      return string.Get(24289);
    end
  end
end

function ArmyBattle.HasOrder(team, order)
  if this.IsDrumPoint(team) then return false end

  for i = 1, this.teamInfos[team].orderCount do
    if this.teamInfos[team].orders[i] == order then
      return true;
    end
  end

  return false;
end

function ArmyBattle.IsEnemy(moral)
  return this.playerMoralColor ~= moral and not this.IsDrumPoint(moral);
end

function ArmyBattle.IsDrumPoint(moral)
  return moral == ArmyMoralColor.Yellow;
end

function ArmyBattle.GetEnemyColor()
  if this.playerMoralColor == ArmyMoralColor.Red then
    return ArmyMoralColor.Blue;
  else
    return ArmyMoralColor.Red;
  end
end

function ArmyBattle.GetColor(moral)
  if moral == ArmyMoralColor.Red then
    return Color.DarkRed;
  elseif moral == ArmyMoralColor.Blue then
    return Color.DarkBlue;
  else
    return Color.VeryLightGreen;
  end
end

function ArmyBattle.CloseAllUI()
  if UI.IsVisible(UIArmyBattle) then
    UI.Close(UIArmyBattle);
  end

  if UI.IsVisible(UIArmyBattleFunction) then
    UI.Close(UIArmyBattleFunction);
  end

  if UI.IsVisible(UIArmyBattleAttack) then
    UI.Close(UIArmyBattleAttack);
  end

  if UI.IsVisible(UIArmyBattleDefence) then
    UI.Close(UIArmyBattleDefence);
  end
end
