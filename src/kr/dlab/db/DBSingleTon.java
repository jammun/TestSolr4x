/**
 * 
 */
package kr.dlab.db;

import java.sql.Connection;
import java.sql.DriverManager;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * @author admin
 *
 */
public class DBSingleTon {
	
	private static DBSingleTon myInstance;
	private static InitialContext ic;
	
	private DBSingleTon() {
	}
	
	public static DBSingleTon getInstance() {
		
		if ( myInstance == null ) {
			myInstance = new DBSingleTon();
		}
		if ( ic == null ) {
			try {
				ic = new InitialContext();
			} catch (NamingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return myInstance;
	}
	
	/************************************* DB연결 *********************************/
	public Connection getConnection(){
		Connection con = null;
		try{
//jetty		 DataSource ds = (DataSource)ic.lookup("MyJNDI");
//tomcat	 DataSource ds = (DataSource)ic.lookup("java:comp/env/jdbc/MyJNDI"); 
			DataSource ds = (DataSource)ic.lookup("java:comp/env/jdbc/MyJNDI");
			con = ds.getConnection();
		}catch(Exception ex){
			ex.printStackTrace();
			System.out.println("DB 연결실패 : "+ex);
			con = null;
		}
		return con;
	}
	/************************************* DB연결 *********************************/

	public void conClose(Connection con){
		try{
			if(con != null) {
				con.close();
			}
		}catch(Exception e){
			e.printStackTrace();
		} finally {
			con = null;
		}
	}

}
