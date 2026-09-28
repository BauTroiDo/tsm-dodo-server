
TextParse = {}

local this = TextParse;

function TextParse.OnClickUrl(url, position)
  local kind = string.match(url, "(%S-)_");
  
  if kind == "Role" then
    local roleId = string.match(url, "_(%S+)");
    --roleId = tonumber(roleId);
    --C:014-009 <查詢玩家資訊> +玩家ID(8) +L(1) +名字(L)
    sendBuffer:Clear();
    sendBuffer:WriteInt64(roleId);
    Network.Send(14, 9, sendBuffer);
    
  elseif kind == "ItemMessage" then
    local index = tonumber(string.match(url, "_(%S+)"));
    
    Chat.ShowItemMessage(index);

  elseif kind == "NpcMessage" then
    local index = tonumber(string.match(url, "_(%S+)"));

    Chat.ShowNpcMessage(index);
        
  elseif kind == "Dungeon" then
    local roomId, locked = string.match(url, "_(%S+)_(%S+)");
    roomId = tonumber(roomId);
    Dungeon.OnClickText(roomId, locked);

  elseif kind == "ItemCheck" then
    local itemId, bagIndex = string.match(url,  "_(%S+)_(%S+)");
    UIItemInfo.SetSortOrder(UICheck.uiController.canvas.sortingOrder + 1);
    local itemSave = Item.GetBagItem(EThings.Bag, tonumber(bagIndex));
    if itemSave ~= nil then
      UIItemInfo.Show(itemSave.Id, itemSave);
    else
      UIItemInfo.Show(tonumber(itemId));
    end
    
  elseif kind == "LuckyBag" then
    local luckykind, index = string.match(url, "_(%S+)_(%S+)");
    luckykind = tonumber(luckykind);
    index = tonumber(index);
    ArmyLuckyBag.SendGetLuckyBagProtocal(luckykind, index);
  end
end

function TextParse.GetSpriteText(atlasName, spriteName, width, height, count, offset)
  if count == nil then count = 1 end
  if offset == nil then offset = 0 end
  
  return string.format("<quad a=%s s=%s size=%d h=%d c=%d o=%d />", atlasName, spriteName, width, height, count, offset);
end