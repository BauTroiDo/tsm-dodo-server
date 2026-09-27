UISell = {}
local this = UISell;

this.name = "UISell";
this.moneyString = '';
this.itemString = '';
this.roleCountString = '';
this.callback = nil;
this.args = {};
this.currentSellId = 0;
this.showRole = nil;
this.dontAsk = {};

--C:84-1
function UISell.Launch(sellId, callback, ...)
  if uisellDatas[sellId] == nil then return end

  if this.currentSellId ~= 0 then return end  
  
  this.moneyString = string.Get(uisellDatas[sellId].moneyString);
  this.itemString = string.Get(uisellDatas[sellId].itemString);
  this.callback = callback;

  if this.roleCountString == '' then
    this.roleCountString = string.Get(22819); 
  end  

  local args = { ... };

  this.args = args;

  sendBuffer:Clear();
  sendBuffer:WriteUInt16(sellId);

  for k, v in pairs(this.args) do
    sendBuffer:WriteUInt16(v);
  end

  Network.Send(84, 1, sendBuffer);

  this.currentSellId = sellId;
end

function UISell.SetAsk(sellId, ask)
  this.dontAsk[sellId] = ask;
end

--S:084-001 <回傳介消資料> +介消編號(2) +結果(1)
function UISell.ReceiveQuery(data)
  local sellId = data:ReadUInt16();
  local kind = data:ReadByte();

  if kind == 0 then
    ShowCenterMessage(string.GetUiSellFail(sellId, data:ReadByte(), data:ReadUInt32()));
    this.currentSellId = 0;
    return;
  end

  local role;
  if this.showRole ~= nil then
    role = this.showRole;
  else
    role = Role.player;
  end

  if kind == 255 then  --使用物品
    local itemId = data:ReadUInt16();
    local count = data:ReadUInt16();
    local bagIndex = Item.GetItemIndex(itemId);
    ShowCheckMessage(
      function(result)
        if result then
          if sellId == 11 then --轉轉樂介消
            local items = Item.GetAllItemIndex(itemId, count);
            sendBuffer:Clear();
            sendBuffer:WriteByte(1);
            sendBuffer:WriteUInt16(sellId);
            sendBuffer:WriteByte(table.Count(items));
            for k, v in pairs(items) do
                sendBuffer:WriteByte(k);
                sendBuffer:WriteUInt16(v);
            end
  
            for k, v in pairs(this.args) do
              sendBuffer:WriteUInt16(v);
            end
            Network.Send(84, 2, sendBuffer);
            
          else
            sendBuffer:Clear();
            sendBuffer:WriteByte(1);
            sendBuffer:WriteUInt16(sellId);
            sendBuffer:WriteByte(bagIndex);
            for k, v in pairs(this.args) do
              sendBuffer:WriteUInt16(v);
            end
            Network.Send(84, 2, sendBuffer);
          end

          PluginManager.UISell(sellId);
        else
          this.Clear();
        end
      end,
      string.format(this.itemString, count, string.Concat("<url=ItemCheck_", itemId, "_", bagIndex, ">", itemDatas[itemId]:GetName(true, true), "</url>")),
      role
    );
  elseif kind == 254 then  --免費使用
    sendBuffer:Clear();
    sendBuffer:WriteByte(2)
    sendBuffer:WriteUInt16(sellId);
    for k, v in pairs(this.args) do
      sendBuffer:WriteUInt16(v);
    end
    Network.Send(84, 2, sendBuffer);

    PluginManager.UISell(sellId);
  elseif Contains(kind, 1, 2, 3) then
    local money = data:ReadUInt32();
    local message = "";
    local uiSellData = uisellDatas[sellId];
    local rolecountId = uiSellData.rolecountId;

    --限購次數字串
    if uiSellData.rolecountId > 0 then
      message = string.format(this.roleCountString, tostring(RoleCount.Get(rolecountId)), tostring(RoleCount.Max(rolecountId)));
    end

    message = string.Concat(string.format(this.moneyString, tostring(money), string.GetCurrency(kind)), " ", message);

    if this.dontAsk[sellId] then
      sendBuffer:Clear();
      sendBuffer:WriteByte(2)
      sendBuffer:WriteUInt16(sellId);
      for k, v in pairs(this.args) do
        sendBuffer:WriteUInt16(v);
      end
      Network.Send(84, 2, sendBuffer);
      PluginManager.UISell(sellId);
    else
      ShowCheckMessage(
        function(result)
          if result then
            sendBuffer:Clear();
            sendBuffer:WriteByte(2)
            sendBuffer:WriteUInt16(sellId);
            for k, v in pairs(this.args) do
              sendBuffer:WriteUInt16(v);
            end
            Network.Send(84, 2, sendBuffer);
            PluginManager.UISell(sellId);
          else
            this.Clear();
          end
        end,
        message,
        role
      );
    end
    end

  this.showRole = nil;
end

--S:084-002 <回傳介消結果> +介消編號(2) +結果(1)
function UISell.UISellResult(data)
  local sellId = data:ReadUInt16();
  local result = data:ReadByte();

  this.Clear();

  if result == 0 then  --失敗
    ShowCenterMessage(string.GetUiSellFail(sellId, data:ReadByte(), data:ReadUInt32()));
    return;
  end

  if this.callback ~= nil then
    this.callback();
  end
end

function UISell.ShowRole(npcId)
  this.showRole = Role.GetRole(EHuman.MapNpc, npcId);
end

function UISell.Clear()
  this.currentSellId = 0;
end