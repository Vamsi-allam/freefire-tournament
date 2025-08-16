import React, { useEffect } from 'react';
import { supabase } from '../supabaseClient';
import { useDispatch, useSelector } from 'react-redux';
import { setSupabaseSession } from '../redux/userSlice';
import { useNavigate } from 'react-router-dom';

const SignIn = () => {
  const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { isAuthenticated, role } = useSelector(state=>state.user);

  useEffect(()=>{
    if (isAuthenticated) {
      navigate(role === 'ADMIN' ? '/admin' : '/user');
    }
  },[isAuthenticated,role,navigate]);

  useEffect(()=>{
    const { data: listener } = supabase.auth.onAuthStateChange(async (event, session)=>{
      if (session) {
        const adminEmail = import.meta.env.VITE_ADMIN_EMAIL;
        const r = session.user.email === adminEmail ? 'ADMIN' : 'USER';
        
        // Call backend to create/login user and create wallet
        try {
          const response = await fetch(`${API_BASE}/auth/google-login`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              email: session.user.email,
              name: session.user.user_metadata?.full_name || session.user.user_metadata?.name || session.user.email?.split('@')[0],
              avatar: session.user.user_metadata?.avatar_url
            })
          });
          
          if (response.ok) {
            const data = await response.json();
            // Store the JWT token from backend
            localStorage.setItem('token', data.token);
            localStorage.setItem('userRole', data.role);
            localStorage.setItem('userName', data.name);
            localStorage.setItem('userEmail', data.email);
            if (data.phone) localStorage.setItem('userPhone', data.phone);
            if (data.avatar) localStorage.setItem('userAvatar', data.avatar);
            
            dispatch(setSupabaseSession({ session, role: data.role }));
            navigate(data.role === 'ADMIN' ? '/admin' : '/user');
          } else {
            console.error('Failed to login/register user in backend');
            // Fallback to original behavior
            dispatch(setSupabaseSession({ session, role: r }));
            navigate(r === 'ADMIN' ? '/admin' : '/user');
          }
        } catch (error) {
          console.error('Error calling backend:', error);
          // Fallback to original behavior
          dispatch(setSupabaseSession({ session, role: r }));
          navigate(r === 'ADMIN' ? '/admin' : '/user');
        }
      }
    });
    return ()=>listener.subscription.unsubscribe();
  },[dispatch,navigate]);

  const handleGoogle = async () => {
    await supabase.auth.signInWithOAuth({ provider: 'google', options: { redirectTo: window.location.origin + '/signin' } });
  };

  return (
    <div className='signin-container' style={{minHeight:'100vh',display:'flex',alignItems:'center',justifyContent:'center'}}>
      <div className='signin-box' style={{textAlign:'center'}}>
        <h2 className='signin-heading'>Sign in</h2>
        <p className='signin-sub-heading'>Continue with your Google account</p>
        <button onClick={handleGoogle} className='signin-button' style={{marginTop:'1rem'}}>Continue with Google</button>
      </div>
    </div>
  );
};

export default SignIn;