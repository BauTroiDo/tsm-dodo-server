Treasure = {}
local this = Treasure;

this.MAX_LEVEL = 10;
this.MAX_STAR = 6;
this.sendOpenRequest = false;

this.treasureItems = {};
this.treasureCombination = {};
this.treasureActivityData = {};

function Treasure.Initialize()
  for k, v in pairs(itemDatas) do
    if v.kind == EItemKind.Treasure then
      local data = {};
      data.itemId = k;
      data.name = v.name;
      data.id = v.skillLink;
      data.cardTexture = v.spare3;
      data.quality = v.quality;
      data.count = 0;
      data.level = 0;
      data.star = 0;
      data.exp = 0;

      this.treasureItems[v.skillLink] = data;
    end
  end

  for _, v in pairs(treasureLvDatas) do
    if this.treasureItems[v.id] ~= nil then
      if this.treasureItems[v.id].lvData == nil then
        this.treasureItems[v.id].lvData = {};
      end

      local data = {};
      data.environmentId = v.environmentId;
      data.characterId = v.characterId;
      data.att = v.att;
      data.value = v.value;
      data.count = v.count;
      data.itemCount = v.itemCount;
      data.moneyCount = v.moneyCount;

      table.insert(this.treasureItems[v.id].lvData, v.level, data);
    end
  end

  for _, v in pairs(treasureStarDatas) do
    if this.treasureItems[v.id] ~= nil then
      if this.treasureItems[v.id].starData == nil then
        this.treasureItems[v.id].starData = {};
      end

      local data = {};
      data.environmentId = v.environmentId;
      data.characterId = v.characterId;
      data.att = v.att;
      data.value = v.value;
      data.count = v.count;
      data.itemCount = v.itemCount;
      data.moneyCount = v.moneyCount;

      table.insert(this.treasureItems[v.id].starData, v.star, data);
    end
  end

  for _, v in pairs(treasureGroupLVDatas) do
    local index = v.id;

    if this.treasureCombination[index] == nil then
      this.treasureCombination[index] = {};
      this.treasureCombination[index].att = {};
      this.treasureCombination[index].value = {};
    end

    this.treasureCombination[index].environmentId = v.environmentId;
    this.treasureCombination[index].characterId = v.characterId;

    this.treasureCombination[index].att[v.level] = v.att;
    this.treasureCombination[index].value[v.level] = v.value;
  end

  for k, v in pairs(this.treasureCombination) do
    if treasureGroupInfoDatas[k] ~= nil then
      v.treasureId = {};
      v.treasureCount = {};
      v.string = treasureGroupInfoDatas[k].string;
      v.level = 0;

      for _, a in pairs(treasureGroupInfoDatas[k].treasureId) do
        if a ~= 0 then
          table.insert(v.treasureId, a);
          table.insert(v.treasureCount, 0);
        end
      end
    end
  end

  for _, v in pairs(treasureActivityCriteriaDatas) do
    local index = v.id;

    if this.treasureActivityData[index] == nil then
      this.treasureActivityData[index] = {};
    end

    this.treasureActivityData[index].string = v.string;
    this.treasureActivityData[index].type = v.type;
  end
end

function Treasure.SortItems()
  table.sort(this.treasureItems,
    function(a, b)
      if a.level == b.level then
        if a.quality == b.quality then
          return a.id > b.id
        else
          return a.quality > b.quality
        end
      else
        return a.level > b.level
      end
    end
  );
end

function Treasure.GetKeyById(id)
  for k, v in pairs(this.treasureItems) do
    if id == v.id then
      return k;
    end
  end

  return 0;
end

function Treasure.GetCardCount(data, lv)
  local count = data.count;

  for i = 1, this.MAX_LEVEL do
    if i <= lv then
      count = count - data.lvData[i].count;
    end
  end

  return count;
end

function Treasure.GetAllBonus()
  local data = {};

  for _, v in pairs(this.treasureCombination) do
    if data[v.environmentId] == nil then
      data[v.environmentId] = {};
      data[v.environmentId].data = {};
    end

    if v.level > 0 then
      local temp = {};
      temp.role = v.characterId;
      temp.att = v.att[v.level];
      temp.value = v.value[v.level];

      table.insert(data[v.environmentId].data, temp);
    end
  end

  for _, v in pairs(this.treasureItems) do
    if v.level > 0 then
      local lvData = v.lvData[v.level];

      if data[lvData.environmentId] == nil then
        data[lvData.environmentId] = {};
        data[lvData.environmentId].data = {};
      end

      local temp = {};
      temp.role = lvData.characterId;
      temp.att = lvData.att;
      temp.value = lvData.value;

      table.insert(data[lvData.environmentId].data, temp);
    end

    if v.star > 0 then
      local starData = v.starData[v.star];

      if data[starData.environmentId] == nil then
        data[starData.environmentId] = {};
        data[starData.environmentId].data = {};
      end

      local temp = {};
      temp.role = starData.characterId;
      temp.att = starData.att;
      temp.value = starData.value;

      table.insert(data[starData.environmentId].data, temp);
    end
  end

  return data;
end

function Treasure.GetCombinationLv(combIndex)
  local lv = 10;

  if this.treasureCombination[combIndex] == nil then return end

  for _, v in pairs(this.treasureCombination[combIndex].treasureId) do
    v = this.GetKeyById(v);
    if this.treasureItems[v].level < lv then
      lv = this.treasureItems[v].level;
    end
  end

  this.treasureCombination[combIndex].level = lv;
end

function Treasure.GetCombAttrText(combIndex, lv)
  local data = this.treasureCombination[combIndex];
  if lv == 0 then return "" end

  local str = string.Concat(this.GetEnvirText(data.environmentId),
    this.GetRoleText(data.characterId),
    ItemData.GetAttrText(data.att[lv]), "+", data.value[lv]);

  if lv == this.treasureCombination[combIndex].level then
    str = string.GetColorText(str, Color.DarkGreen);
  else
    str = string.GetColorText(str, Color.Black);
  end

  return str;
end

function Treasure.GetCardAttrText(data, lv)
  if lv == 0 then return "" end

  return string.Concat(
    this.GetEnvirText(data[lv].environmentId),
    this.GetRoleText(data[lv].characterId),
    ItemData.GetAttrText(data[lv].att), "+", data[lv].value);
end

function Treasure.GetRoleText(id)
  local str = "";
  local data = treasureRoleCriteriaDatas[id];

  if data.country ~= 255 then
    str = string.Concat(str, string.GetMoral(data.country));
  end

  if data.element ~= 255 then
    str = string.Concat(str, string.GetElement(data.element), string.Get(99684));
  end

  if data.turn == 0 then
    str = string.Concat(str, string.Get(90107));
  elseif data.turn ~= 255 then
    str = string.Concat(str, string.Get(23214 + data.turn));
  end

  if data.collectBookId ~= 65535 then
    local warriorName = npcDatas[collectWarriorSortDatas[data.collectBookId].ids[1]].name;
    str = string.Concat(str, warriorName);
  elseif data.role == 1 then
    str = string.Concat(str, string.Get(20502)); -- 玩家
  elseif data.role == 2 then
    str = string.Concat(str, string.Get(10403)); -- 武將
  end

  return str;
end

function Treasure.GetEnvirText(id, showAll)
  if id == 0 then
    if showAll then
      return string.Get(24681);
    end
  else
    return string.Get(Treasure.treasureActivityData[id].string);
  end

  return "";
end

function Treasure.IsMaxLevel(id)
  if this.treasureItems[id] == nil then return false end
  return this.treasureItems[id].level == this.MAX_LEVEL;
end

function Treasure.Clear()
  table.Clear(this.treasureItems);
  table.Clear(this.treasureCombination);
  table.Clear(this.treasureActivityData);
end