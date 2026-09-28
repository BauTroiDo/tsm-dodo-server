ETeamInvitation = {
  Invite = 1,
  Join = 2,
}

ETeamState = {
  None = 0, --未組隊
  Leader = 1, --隊長
  Adviser = 2, --軍師
  TeamMembers = 3 --隊員
}

Team = {}
local this = Team;

--const
this.maxMember = 5;

--var
this.leaders = {}; --Key = 隊長ID Value = 軍師ID
this.members = {}; --Key = 玩家ID Value = 隊長ID

this.inviteStr = nil;

function Team.Clear()
  table.Clear(this.leaders);
  table.Clear(this.members);
end

function Team.Invite(role)
  if role == nil then return end
  if Role.player == nil then return end

  --攻城戰觀戰中
  if Role.player.data.isWatching then return end

  local roleId = role.index;

  --戰鬥中
  if role.war ~= EWar.None then
    if role.war == EWar.Guest then
      ShowCenterMessage(string.Get(50085));
    else
      ShowCenterMessage(string.Get(40072));
    end

    return;
  end

  --GM
  if Role.IsGM(roleId) then
    ShowCenterMessage(string.Get(40006));
    return;
  end

  --場景限制
  if SceneManager.CheckLimit(SceneManager.sceneId, ESceneLimit.NoTeam) then
    ShowCenterMessage(string.Get(40264));
    return;
  end

  local invitation = Invitation.Get(EInvitation.Team, roleId);

  if this.IsLeader(Role.playerId) then
    --對方已要求加入，直接回覆同意要求
    if invitation ~= nil and invitation.state == ETeamInvitation.Join then
      Invitation.Reply(EInvitation.Team, roleId, true);
      return;
    end

    --已在隊伍中
    if this.GetLeader(Role.playerId) == this.GetLeader(roleId) then
      ShowCenterMessage(string.Get(20385));
      return;
    end
    
    --已有隊伍
    if not this.IsAlone(roleId) then
      ShowCenterMessage(string.Get(20384));
      return;
    end

    --隊伍已滿
    local members = this.GetMember(Role.playerId, true);
    if table.Count(members) >= this.maxMember then
      ShowCenterMessage(string.Get(40143));
      return;
    end

    --C:013-007 <邀請組隊> +玩家ID(8)
    sendBuffer:Clear()
    sendBuffer:WriteInt64(roleId);
    Network.Send(13, 7, sendBuffer);
    
    this.inviteStr = string.format(string.Get(20164), role.name);

  elseif this.IsAlone(Role.playerId) then
    --對方已邀請加入，先拒絕再發出邀請
    if invitation ~= nil and invitation.state == ETeamInvitation.Invite then
      Invitation.Reply(EInvitation.Team, roleId, false);
    end

    if this.IsAlone(roleId) then
      --C:013-007 <邀請組隊> +玩家ID(8)
      sendBuffer:Clear()
      sendBuffer:WriteInt64(roleId);
      Network.Send(13, 7, sendBuffer);

      this.inviteStr = string.format(string.Get(20164), role.name);
    else
      local leader = this.GetLeader(roleId);
      
      if leader == nil then return end

      --隊伍已滿
      local members = this.GetMember(roleId, true);
      if table.Count(members) >= this.maxMember then
        ShowCenterMessage(string.Get(40142));
        return;
      end
      
      --對方已邀請加入，直接回覆同意邀請
      if invitation ~= nil and invitation.state == ETeamInvitation.Invite then
        Invitation.Reply(EInvitation.Team, roleId, true);
        return;
      end
      
      --C:013-001 <要求組隊> +玩家ID(8)
      sendBuffer:Clear()
      sendBuffer:WriteInt64(leader.index);
      Network.Send(13, 1, sendBuffer);

      this.inviteStr = string.format(string.Get(20383), role.name);
    end
  end
end

function Team.Leave()
  if this.members[Role.playerId] == nil then return end

  --C:013-004 <離開隊伍> +隊長玩家ID(8)
  sendBuffer:Clear()
  sendBuffer:WriteInt64(this.members[Role.playerId]);
  Network.Send(13, 4, sendBuffer);
end

local function AddMember(leaderId, roleId)
  logWarn(string.Concat("AddMember ", leaderId, " ", roleId));
  local leader = Role.players[leaderId];

  if this.leaders[leaderId] == nil then
    this.leaders[leaderId] = 0;
    this.members[leaderId] = leaderId;

    if leader ~= nil then
      local followNpcs = Role.GetFollowNpcs(leaderId);
      for k, v in pairs(followNpcs) do
        v:Teleport(leader.position);
        v:SetVisible(ERoleVisible.Hide);
      end

      local cartNpc = Role.cartNpcs[leaderId];
      if cartNpc ~= nil then
        cartNpc:Teleport(leader.position);
        cartNpc:SetVisible(ERoleVisible.Hide);
      end

      leader:UpdateViewTeam();
    end
  end

  this.members[roleId] = leaderId;

  if Role.players[roleId] ~= nil then
    if leader ~= nil then
      Role.players[roleId]:Teleport(leader.position);
      Role.players[roleId]:UpdateSpeed(leader.speed);
    end

    local followNpcs = Role.GetFollowNpcs(roleId);
    for k, v in pairs(followNpcs) do
      v:Teleport(Role.players[roleId].position);
      v:SetVisible(ERoleVisible.Hide);
    end

    local cartNpc = Role.cartNpcs[roleId];
    if cartNpc ~= nil then
      cartNpc:Teleport(Role.players[roleId].position);
      cartNpc:SetVisible(ERoleVisible.Hide);
    end

    Role.players[roleId]:UpdateViewTeam();
  end
end

local function RemoveMember(roleId)
  logWarn(string.Concat("RemoveMember ", roleId));
  this.members[roleId] = nil;

  if Role.players[roleId] ~= nil then
    local followNpcs = Role.GetFollowNpcs(roleId);
    for k, v in pairs(followNpcs) do
      v:Teleport(Role.players[roleId].position);
      v:SetVisible(ERoleVisible.Visible);
    end

    local cartNpc = Role.cartNpcs[roleId];
    if cartNpc ~= nil then
      cartNpc:Teleport(Role.players[roleId].position);
      cartNpc:SetVisible(ERoleVisible.Visible);
    end

    Role.players[roleId]:UpdateSpeed();
    Role.players[roleId]:UpdateViewTeam();
  end
end

local function RemoveLeader(roleId)
  this.leaders[roleId] = nil;

  for k, v in pairs(this.members) do
    if v == roleId then
      RemoveMember(k);
    end
  end
end

function Team.AddMember(leaderId, roleId, showMessage)
  leader = Role.players[leaderId];

  if leader == nil then return end

  role = Role.players[roleId];

  if role == nil then return end

  --實際加入隊伍
  AddMember(leaderId, roleId);

  Invitation.Check(EInvitation.Team);

  if UI.IsVisible(UITeam) then
    UITeam.UpdateUI();
  end

  if UI.IsVisible(UIMiniMap) then
    UIMiniMap.UpdateTeam();
  end

  for k, v in pairs(Role.players) do
    v:UpdateViewVisible();
  end

  if showMessage == false then return end

  if leader == this.GetLeader(Role.playerId) then
    Chat.AddMessage(EChannel.System, string.format(string.Get(20731), role.name));
    ShowCenterMessage(string.format(string.Get(20731), role.name));
  end
end

function Team.RemoveMember(roleId, showMessage)
  if this.members[roleId] == nil then return end

  local role = Role.players[roleId];
  if role == nil then return end

  local leader = Role.players[this.members[roleId]];
  if leader == nil then return end

  local myTeam = leader.index == this.members[Role.playerId];
  
  --離隊的人是否隊長
  if leader.index == roleId then
    --玩家的隊伍
    if myTeam and showMessage ~= false then
      ShowCenterMessage(string.Concat(role.name, string.Get(40092)));
      Chat.AddMessage(EChannel.System, string.Concat(role.name, string.Get(40092)));
    end

    --解散隊伍
    RemoveLeader(roleId);
  else
    --若是軍師則將設無軍師
    if this.leaders[leader.index] == roleId then
      this.leaders[leader.index] = 0;
    end

    --玩家的隊伍
    if myTeam and showMessage ~= false then
      ShowCenterMessage(string.Concat(role.name, string.Get(40093)));
      Chat.AddMessage(EChannel.System, string.Concat(role.name, string.Get(40093)));
    end

    --實際離開隊伍
    RemoveMember(roleId);

    --檢查是否只剩隊長一人
    local members = {};
    for k, v in pairs(this.members) do
      if v == leader.index then
        table.insert(members, k);
      end
    end

    --解散隊伍
    if table.maxn(members) <= 1 then
      RemoveLeader(leader.index);
    end
  end

  for k, v in pairs(Role.players) do
    v:UpdateViewVisible();
  end

  if UI.IsVisible(UITeam) then
    UITeam.UpdateUI();
  end

  if UI.IsVisible(UIMiniMap) then
    UIMiniMap.UpdateTeam();
  end
end

function Team.AddAdviser(roleId, showMessage)
  if this.members[roleId] == nil then return end

  this.leaders[this.members[roleId]] = roleId;

  if showMessage ~= false then
    if Role.players[roleId] ~= nil and this.GetLeader(roleId) == this.GetLeader(Role.playerId) then
      ShowCenterMessage(string.format(string.Get(20386), Role.players[roleId].name));
    end
  end

  local members = this.GetMember(roleId, true);
  for k, v in pairs(members) do
    v:UpdateViewTeam();
  end

  if UI.IsVisible(UITeam) then
    UITeam.UpdateUI();
  end

  if UI.IsVisible(UIMiniMap) then
    UIMiniMap.UpdateTeam();
  end
end

function Team.RemoveAdviser(roleId)
  if this.members[roleId] == nil then return end

  this.leaders[this.members[roleId]] = 0;

  local members = this.GetMember(roleId, true);
  for k, v in pairs(members) do
    v:UpdateViewTeam();
  end

  if UI.IsVisible(UITeam) then
    UITeam.UpdateUI();
  end

  if UI.IsVisible(UIMiniMap) then
    UIMiniMap.UpdateTeam();
  end
end

function Team.IsLeader(roleId)
  return this.leaders[roleId] ~= nil;
end

function Team.IsMember(roleId)
  return this.members[roleId] ~= nil;
end

function Team.IsAlone(roleId)
  return this.leaders[roleId] == nil and this.members[roleId] == nil;
end

function Team.IsAdviser(roleId)
  if this.members[roleId] == nil then return false end

  return this.leaders[this.members[roleId]] == roleId;
end

function Team.GetLeader(roleId)
  if this.members[roleId] ~= nil then
    return Role.players[this.members[roleId]];
  else
    return Role.players[roleId];
  end
end

function Team.GetAdviser(roleId)
  if this.members[roleId] == nil then return nil end
  
  return Role.players[this.leaders[this.members[roleId]]];
end

function Team.GetTeamState(roleId)
  if this.IsAlone(roleId) then
    return ETeamState.None;
  else
    if this.IsLeader(roleId) then
      return ETeamState.Leader;
    elseif this.IsAdviser(roleId) then
      return ETeamState.Adviser;
    else
      return ETeamState.TeamMembers;
    end 
  end
end

function Team.GetMember(roleId, includeSelf)
  local result = {};
  if this.members[roleId] ~= nil then
    local leaderId = this.members[roleId];
    for k, v in pairs(this.members) do
      if v == leaderId then
        if k ~= roleId or includeSelf then
          if Role.players[k] ~= nil then
            table.insert(result, Role.players[k]);
          end
        end
      end
    end
  else
    if includeSelf and Role.players[roleId] ~= nil then
      table.insert(result, Role.players[roleId]);
    end
  end

  return result;
end