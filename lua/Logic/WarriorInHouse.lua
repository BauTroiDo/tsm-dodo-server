WarriorInHouse = {}
local this = WarriorInHouse;

this.allNpc = {};
this.totalCombination = 0;

local npcInHouse = {};
local serHouseWarriors = {};

local npcMaxCount = 4;
local npcLevel = 120;
local npcTurn = 2;

local blockSendData = 1;

function WarriorInHouse.Initialize()
  for i = 1, 8 do
    npcInHouse[i] = {};
    npcInHouse[i].npcIds = {};
    npcInHouse[i].preNPCIds = {};
    npcInHouse[i].maxCount = 0;
    npcInHouse[i].combination = 0;
  end
end

--打開放置武將介面
function WarriorInHouse.OpenEditor(chosenBlock)
  local data = npcInHouse[chosenBlock].npcIds;

  table.Clear(npcInHouse[chosenBlock].preNPCIds);

  for i = 1, table.Count(data) do
    if data[i] == nil then
      npcInHouse[chosenBlock].preNPCIds[i] = 0;
    else
      table.insert(npcInHouse[chosenBlock].preNPCIds, data[i]);
    end
  end
end

--關閉放置武將介面
function WarriorInHouse.CloseEditor(chosenBlock, change)
  blockSendData = chosenBlock;
  if change == false then
    this.SetPreNpcData();
  else
    this.SendHouseWarriorData();
  end
end

function WarriorInHouse.CheckScore()
  if blockSendData == nil then return end
  local house = HouseManager.GetCurrentBuilding();
  local floor = HouseManager.GetCurrentFloor();
  local chosenBlock = this.TransformHouseToBlock(house, floor);

  if this.GetBlockNpcCount(chosenBlock) > this.GetBlockMaxCount(chosenBlock) then
    for i = this.GetBlockMaxCount(chosenBlock) + 1, npcMaxCount do
      npcInHouse[blockSendData].npcIds[i] = 0;
    end

    this.SendHouseWarriorData();
  end
end

function WarriorInHouse.SendHouseWarriorData()
  if blockSendData == nil then return end
  local house, floor = this.TransformBlockToHouse(blockSendData);

  --C:106-003 <設定小屋武將> +<<武將ID(2)>> * 4 +小屋編號(1) +樓層(1)
  sendBuffer:Clear();
  for i = 1, 4 do
    sendBuffer:WriteUInt16(npcInHouse[blockSendData].npcIds[i] or 0);
  end
  sendBuffer:WriteByte(house);
  sendBuffer:WriteByte(floor);
  Network.Send(106, 3, sendBuffer);
end

function WarriorInHouse.ReceiveHouseWarrior(houseWarriors)
  serHouseWarriors = houseWarriors;
  blockSendData = this.TransformHouseToBlock(HouseManager.GetCurrentBuilding(), HouseManager.GetCurrentFloor());
  npcInHouse[blockSendData].npcIds = houseWarriors;
  this.CreateHouseWarriorByServer();
end

function WarriorInHouse.CreateHouseWarriorByServer()
  for k, v in pairs(Role.houseWarriors) do
    Role.HouseWarriorDisappear(k);
  end

  for k, v in pairs(serHouseWarriors) do
    Role.HouseWarriorAppear(k, v);
  end
end

function WarriorInHouse.GetAllNpc()
  table.Clear(this.allNpc);

  if table.Count(Role.followNpcs[Role.playerId]) ~= 0 then
    for i = 1, Role.maxFollowNpc do

      if Role.followNpcs[Role.playerId] == nil then
        break;
      end

      local roleTemp = Role.followNpcs[Role.playerId][i];

      if roleTemp ~= nil then
        if roleTemp.data.canGrow ~= 0 and roleTemp.data.canGrow ~= 2 then
          local npc = {}
          local npcData = npcDatas[roleTemp.npcId];
          if roleTemp:GetAttribute(EAttribute.Lv) >= npcLevel and roleTemp:GetAttribute(EAttribute.Turn) >= npcTurn then

            npc.npcId = roleTemp.npcId;
            npc.name = roleTemp.name;
            npc.hp = roleTemp:GetAttribute(EAttribute.Hp);
            npc.level = roleTemp:GetAttribute(EAttribute.Lv);
            npc.turn = npcData.turn;
            npc.element = npcData.element;

            this.allNpc[npc.npcId] = npc;
          end
        end
      end
    end
  end

  if table.Count(Inn.npcs) ~= 0 then
    for i = 1, Inn.maxSpace do
      if Inn.npcs[i] ~= nil then
        local npc = Inn.npcs[i];
        local npcData = npcDatas[npc.npcId];

        if npc.level >= npcLevel and npcData.turn >= npcTurn then

          npc.turn = npcData.turn;
          npc.element = npcData.element;
          this.allNpc[npc.npcId] = npc;
        end

      end
    end
  end

  if table.Count(Cart.followNpcs) ~= 0 then
    for i = 1, Cart.GetCartSize() do
      if Cart.followNpcs[i] ~= nil then
        local npc = Cart.followNpcs[i];
        local npcData = npcDatas[npc.npcId];

        if npc.Lv >= npcLevel and npcData.turn >= npcTurn then

          npc.level = npc.Lv;
          npc.turn = npcData.turn;
          npc.element = npcData.element;
          this.allNpc[npc.npcId] = npc;
        end
      end
    end
  end
end

function WarriorInHouse.SetNpcData(house, floor, data)
  local index = this.TransformHouseToBlock(house, floor);

  table.Clear(npcInHouse[index].npcIds);
  table.Clear(npcInHouse[index].preNPCIds);
  npcInHouse[index].maxCount = 0;
  npcInHouse[index].combination = 0;

  for i = 1, 4 do
    local id = data:ReadUInt16();
      npcInHouse[index].npcIds[i] = id;
  end

  this.GetFloorCombinationAttribute(index);
end

--取得放在小屋內的所有武將
function WarriorInHouse.GetAllNpcInHouse()
  local data = {};
  for i = 1, table.Count(npcInHouse) do
    local temp = npcInHouse[i].npcIds;
    for k = 1, this.GetBlockMaxCount(i) do
      if temp[k] ~= 0 or temp[k] ~= nil then
        table.insert(data, temp[k]);
      end
    end
  end

  return data;
end

function WarriorInHouse.SetPreNpcData()
  local data = npcInHouse[blockSendData].preNPCIds;

  table.Clear(npcInHouse[blockSendData].npcIds);

  for i = 1, table.Count(data) do
    table.insert(npcInHouse[blockSendData].npcIds, data[i]);
  end

  UI.Close(UIHouseSettingFunction);

  this.GetFloorCombinationAttribute(blockSendData);
end

function WarriorInHouse.TransformHouseToBlock(house, floor)
  if floor % 2 == 0 then
    return floor * house;
  else
    return house * 2 - 1;
  end
end

function WarriorInHouse.TransformBlockToHouse(chosenBlock)
  local house, floor;
  if chosenBlock % 2 == 0 then
    house = chosenBlock / 2;
    floor = 2;
  else
    house = chosenBlock / 2 + 0.5;
    floor = 1;
  end

  return house, floor;
end

function WarriorInHouse.SetNpcInBlock(chosenBlock, npcId)
  for i = 1, this.GetBlockMaxCount(chosenBlock) do
    if npcInHouse[chosenBlock].npcIds[i] == nil or npcInHouse[chosenBlock].npcIds[i] == 0 then
      npcInHouse[chosenBlock].npcIds[i] = npcId;
      break;
    end
  end
end

function WarriorInHouse.RemoveNpcFromBlock(chosenBlock, index)
  npcInHouse[chosenBlock].npcIds[index] = nil;
end

function WarriorInHouse.ExchangeNpc(chosenBlock, npcId, index)
  npcInHouse[chosenBlock].npcIds[index] = npcId;
end

function WarriorInHouse.GetHouseNpcId(chosenBlock, index)
  return npcInHouse[chosenBlock].npcIds[index];
end

function WarriorInHouse.GetNpcData(npcId)
  return this.allNpc[npcId];
end

function WarriorInHouse.GetMaxCount(house, floor)
  return this.GetBlockMaxCount(this.TransformHouseToBlock(house, floor));
end

function WarriorInHouse.GetNpcCount(house, floor)
  return this.GetBlockNpcCount(this.TransformHouseToBlock(house, floor));
end

function WarriorInHouse.GetBlockNpcCount(chosenBlock)
  local count = 0;
  for i = 1, npcMaxCount do
    if npcInHouse[chosenBlock].npcIds[i] ~= nil and npcInHouse[chosenBlock].npcIds[i] ~= 0 then
      count = count + 1;
    end
  end

  return count;
end

function WarriorInHouse.GetBlockMaxCount(chosenBlock)
  npcInHouse[chosenBlock].maxCount = 0;

  local house, floor = this.TransformBlockToHouse(chosenBlock);

  for i = 1, npcMaxCount do
    if numberOfHouseWarriorDatas[i].score <= HouseManager.GetFloorScore(house, floor) then
      npcInHouse[chosenBlock].maxCount = numberOfHouseWarriorDatas[i].number;
    end
  end

  return npcInHouse[chosenBlock].maxCount;
end

function WarriorInHouse.CheckCombination(index, chosenBlock)
  local data = warriorCombinationDatas[index];
  local flag = 0;
  local countOfData = 0;

  npcInHouse[chosenBlock].combination = 0;

  for i = 1, npcMaxCount do
    if data.warriors[i] ~= 0 then
      countOfData = countOfData + 1;
    end
  end

  for i = 1, table.Count(npcInHouse[chosenBlock].npcIds) do
    for j = 1, npcMaxCount do
      local sortIndex = collectWarriorSortDatas[data.warriors[j]];
      if sortIndex ~= nil then
        for k = 1, table.Count(sortIndex.ids) do
          if npcInHouse[chosenBlock].npcIds[i] == sortIndex.ids[k] then
            flag = flag + 1;
            break;
          end
        end
      end
    end
  end

  if flag == countOfData then
    npcInHouse[chosenBlock].combination = index;
    return true;
  else
    return false;
  end

end

function WarriorInHouse.GetSortedCombination()
  local data = {};

  local combinationInUsed = {};
  local combinationNotUse = {};

  this.totalCombination = 0;

  for i = 1, table.Count(warriorCombinationDatas) do
    local hasCombination = false;
    for j = 1, table.Count(npcInHouse) do
      if npcInHouse[j].combination == warriorCombinationDatas[i].index then
        table.insert(combinationInUsed, warriorCombinationDatas[i]);
        this.totalCombination = this.totalCombination + 1;
        hasCombination = true;
        break;
      end;
    end

    if hasCombination == false then
      table.insert(combinationNotUse, warriorCombinationDatas[i]);
    end
  end

  for i = 1, table.Count(combinationInUsed) do
    table.insert(data, combinationInUsed[i]);
  end

  for i = 1, table.Count(combinationNotUse) do
    table.insert(data, combinationNotUse[i]);
  end

  return data;
end

function WarriorInHouse.GetCombinationAttribute(index)
  local data = warriorCombinationDatas[index];
  if data == nil then
    return "";
  else
    return string.Concat(ItemData.GetAttrText(data.values.kind), "+", data.values.value);
  end;
end

function WarriorInHouse.GetFloorCombinationAttribute(chosenBlock)
  if this.GetBlockMaxCount(chosenBlock) == 0 then return string.Get(23576) end
  if this.GetBlockNpcCount(chosenBlock) == 0 then return string.Get(23576) end

  for i = 1, table.Count(warriorCombinationDatas) do
    if this.CheckCombination(i, chosenBlock) then
      return this.GetCombinationAttribute(i);
    end
  end

  return string.Get(23576);
end
