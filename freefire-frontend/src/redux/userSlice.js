import { createSlice } from '@reduxjs/toolkit';
 
const initialState = {
  userData: {
    name: localStorage.getItem('userName') || null,
    email: localStorage.getItem('userEmail') || null,
    phone: localStorage.getItem('userPhone') || null,
    gameId: localStorage.getItem('userGameId') || null,
    role: localStorage.getItem('userRole') || null,
    avatar: localStorage.getItem('userAvatar') || null
  },
  isAuthenticated: !!localStorage.getItem('token') || !!localStorage.getItem('supabaseSession'),
  role: localStorage.getItem('userRole') || null,
  token: localStorage.getItem('token') || localStorage.getItem('supabaseAccessToken') || null
};
 
const userSlice = createSlice({
  name: 'user',
  initialState,
  reducers: {
    setUser: (state, action) => {
      state.userData = {
        name: action.payload.name,
        email: action.payload.email,
        phone: action.payload.phone,
        gameId: action.payload.gameId,
        role: action.payload.role,
        avatar: action.payload.avatar || null
      };
      state.isAuthenticated = true;
      state.role = action.payload.role;
      state.token = action.payload.token;
      localStorage.setItem('token', action.payload.token);
      localStorage.setItem('userRole', action.payload.role);
      localStorage.setItem('userName', action.payload.name);
      localStorage.setItem('userEmail', action.payload.email);
      localStorage.setItem('userPhone', action.payload.phone);
      if (action.payload.avatar) localStorage.setItem('userAvatar', action.payload.avatar);
    },
    setSupabaseSession: (state, action) => {
      const { session, role } = action.payload;
      if (!session) return;
      const user = session.user;
      state.userData = {
        name: user.user_metadata?.full_name || user.user_metadata?.name || user.email?.split('@')[0],
        email: user.email,
        phone: localStorage.getItem('userPhone') || user.user_metadata?.phone || null,
        gameId: localStorage.getItem('userGameId') || null,
        role: role,
        avatar: localStorage.getItem('userAvatar') || user.user_metadata?.avatar_url || null
      };
      state.isAuthenticated = true;
      state.role = role;
      state.token = session.access_token;
      localStorage.setItem('supabaseSession', JSON.stringify(session));
      localStorage.setItem('supabaseAccessToken', session.access_token);
      localStorage.setItem('userRole', role);
      localStorage.setItem('userName', state.userData.name || '');
      localStorage.setItem('userEmail', state.userData.email || '');
      if (state.userData.phone) localStorage.setItem('userPhone', state.userData.phone);
      if (state.userData.gameId) localStorage.setItem('userGameId', state.userData.gameId);
      if (state.userData.avatar) localStorage.setItem('userAvatar', state.userData.avatar);
    },
    updateProfile: (state, action) => {
      // Update user profile with new information, preserving existing data
      if (action.payload.displayName) {
        state.userData.name = action.payload.displayName;
        localStorage.setItem('userName', action.payload.displayName);
      }
      if (action.payload.phoneNumber) {
        state.userData.phone = action.payload.phoneNumber;
        localStorage.setItem('userPhone', action.payload.phoneNumber);
      }
      if (action.payload.gameId) {
        state.userData.gameId = action.payload.gameId;
        localStorage.setItem('userGameId', action.payload.gameId);
      }
      // Preserve avatar if not explicitly updated
      if (action.payload.avatar !== undefined) {
        state.userData.avatar = action.payload.avatar;
        if (action.payload.avatar) {
          localStorage.setItem('userAvatar', action.payload.avatar);
        } else {
          localStorage.removeItem('userAvatar');
        }
      }
    },
    clearUser: (state) => {
      state.userData = {
        name: null,
        email: null,
        phone: null,
        gameId: null,
        role: null,
        avatar: null
      };
      state.isAuthenticated = false;
      state.role = null;
      state.token = null;
      localStorage.removeItem('token');
      localStorage.removeItem('userRole');
      localStorage.removeItem('userName');
      localStorage.removeItem('userEmail');
      localStorage.removeItem('userPhone');
      localStorage.removeItem('userGameId');
      localStorage.removeItem('userAvatar');
      localStorage.removeItem('supabaseSession');
      localStorage.removeItem('supabaseAccessToken');
      localStorage.removeItem('needsProfileCompletion');
    },
    // Optional alias for clarity in components
    logout: (state) => {
      // reuse logic
      const s = state;
      s.userData = { name: null, email: null, phone: null, gameId: null, role: null, avatar: null };
      s.isAuthenticated = false;
      s.role = null;
      s.token = null;
      try {
        localStorage.removeItem('token');
        localStorage.removeItem('userRole');
        localStorage.removeItem('userName');
        localStorage.removeItem('userEmail');
        localStorage.removeItem('userPhone');
        localStorage.removeItem('userGameId');
        localStorage.removeItem('userAvatar');
        localStorage.removeItem('supabaseSession');
        localStorage.removeItem('supabaseAccessToken');
        localStorage.removeItem('needsProfileCompletion');
      } catch {}
    },
    initializeFromStorage: (state) => {
      const sessionStr = localStorage.getItem('supabaseSession');
      if (sessionStr) {
        try {
          const session = JSON.parse(sessionStr);
          state.isAuthenticated = true;
          state.token = session.access_token;
          state.role = localStorage.getItem('userRole');
          state.userData = {
            name: localStorage.getItem('userName'),
            email: localStorage.getItem('userEmail'),
            phone: localStorage.getItem('userPhone'),
            gameId: localStorage.getItem('userGameId'),
            role: localStorage.getItem('userRole'),
            avatar: localStorage.getItem('userAvatar')
          };
          return;
        } catch {}
      }
      const token = localStorage.getItem('token');
      if (token) {
        state.isAuthenticated = true;
        state.token = token;
        state.role = localStorage.getItem('userRole');
        state.userData = {
          name: localStorage.getItem('userName'),
          email: localStorage.getItem('userEmail'),
          phone: localStorage.getItem('userPhone'),
          gameId: localStorage.getItem('userGameId'),
          role: localStorage.getItem('userRole'),
          avatar: localStorage.getItem('userAvatar')
        };
      }
    }
  },
});
 
export const { setUser, clearUser, initializeFromStorage, setSupabaseSession, updateProfile } = userSlice.actions;
export const selectUser = (state) => state.user.userData;
export const selectIsAuthenticated = (state) => state.user.isAuthenticated;
export const selectRole = (state) => state.user.role;
 
export default userSlice.reducer;