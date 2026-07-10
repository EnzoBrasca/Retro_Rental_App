import React from 'react';
import { useAuth } from '../context/AuthContext';
import { AuthScreen } from '../screens/AuthScreen';
import { AdminScreen } from '../screens/AdminScreen';
import { EmployeeArea } from './EmployeeArea';

/**
 * Role-based routing (the mockup's "validation between administrator and
 * employee"): no session → login; admin → analytics; employee → tab app.
 */
export function RootNavigator() {
  const { user } = useAuth();
  if (!user) return <AuthScreen />;
  if (user.role === 'admin') return <AdminScreen />;
  return <EmployeeArea />;
}
