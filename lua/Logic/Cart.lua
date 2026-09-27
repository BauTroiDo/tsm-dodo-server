
Cart = {}
local this = Cart;

--var
this.followNpcs = {};

function Cart.Clear()
  table.Clear(this.followNpcs);
end

function Cart.AddNpc(index, npcId, level, hp, name)
  if this.followNpcs[index] == nil then
    this.followNpcs[index] = {};
  end

  this.followNpcs[index].npcId = npcId;
  this.followNpcs[index].Lv = level;
  this.followNpcs[index].hp = hp;
  this.followNpcs[index].name = string.CheckNpcName(npcId,name);

  if UI.IsVisible(UITeam) then
    UITeam.UpdateUI();
  end
end

function Cart.RemoveNpc(index)
  if this.followNpcs[index] == nil then return end

  this.followNpcs[index] = nil;

  if UI.IsVisible(UITeam) then
    UITeam.UpdateUI();
  end
end

function Cart.GetCartNpcId(turn, career)
  if turn == 1 then
    return 30006;
  elseif turn == 2 then
    if career == ECareer.XiaKe then
      return 30014;
    elseif career == ECareer.BaWang then
      return 30016;
    elseif career == ECareer.XianShi then
      return 30013;
    elseif career == ECareer.DiXian then
      return 30015;
    end
  elseif turn == 3 then
    if career == ECareer.XiaKe then
      return 30019;
    elseif career == ECareer.BaWang then
      return 30021;
    elseif career == ECareer.XianShi then
      return 30018;
    elseif career == ECareer.DiXian then
      return 30020;
    end
  end

  return 0;
end

function Cart.GetCartSize()
  if Role.player == nil or Role.player:GetAttribute(EAttribute.Turn) < 1 then
    return 0;
  end

  if Role.player:GetAttribute(EAttribute.Turn) < 2 then
    return 2;
  end

  return 4;
end
