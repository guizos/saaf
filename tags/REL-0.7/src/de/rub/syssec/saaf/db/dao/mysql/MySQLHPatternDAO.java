/* SAAF: A static analyzer for APK files.
 * Copyright (C) 2013  syssec.rub.de
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.rub.syssec.saaf.db.dao.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.mysql.jdbc.Statement;

import de.rub.syssec.saaf.analysis.steps.heuristic.HPattern;
import de.rub.syssec.saaf.db.dao.exceptions.DAOException;
import de.rub.syssec.saaf.db.dao.exceptions.DuplicateEntityException;
import de.rub.syssec.saaf.db.dao.exceptions.NoSuchEntityException;
import de.rub.syssec.saaf.db.dao.interfaces.NuHPatternDAO;
import de.rub.syssec.saaf.model.analysis.HPatternInterface;
import de.rub.syssec.saaf.model.analysis.PatternType;

/**
 * @author Tilman Bender <tilman.bender@rub.de>
 *
 */
public class MySQLHPatternDAO implements NuHPatternDAO {

	private static final String DB_COLUMN_ID = "id";
	private static final String DB_COLUMN_PATTERN = "pattern";
	private static final String DB_COLUMN_SEARCHIN = "enum_searchin";
	private static final String DB_COLUMN_HVALUE = "heuristic_value";
	private static final String DB_COLUMN_DESCR = "description";
	private static final String DB_COLUMN_ACTIVE = "active";
	
	private static final String QUERY_UPDATE = "UPDATE heuristic_pattern SET "	+DB_COLUMN_PATTERN+"=?, "
																				+DB_COLUMN_SEARCHIN+"=?, "
																				+DB_COLUMN_HVALUE+"=?, "
																				+DB_COLUMN_DESCR+"=?,"
																				+DB_COLUMN_ACTIVE +"=? WHERE "
																				+DB_COLUMN_ID+"=?";
	private static final String QUERY_INSERT = "INSERT INTO heuristic_pattern("	+DB_COLUMN_PATTERN+","
																				+DB_COLUMN_SEARCHIN+","
																				+DB_COLUMN_HVALUE+","
																				+DB_COLUMN_DESCR+","
																				+DB_COLUMN_ACTIVE+")VALUES(?,?,?,?,?)";
	private static final String QUERY_READ = "SELECT * FROM heuristic_pattern WHERE "+DB_COLUMN_ID+"=?";

	private static final String QUERY_DELETE = "DELETE FROM heuristic_pattern WHERE "
			+ DB_COLUMN_ID + "=?";
	private static final String QUERY_READ_ALL = "SELECT * FROM heuristic_pattern";
	private static final String DB_QUERY_DELETE_ALL = "DELETE FROM heuristic_pattern";
	private static final String DB_QUERY_FIND_EXISITING = "SELECT * FROM heuristic_pattern WHERE "+DB_COLUMN_PATTERN+"=? AND "
																									+DB_COLUMN_SEARCHIN+"=? AND "
																									+DB_COLUMN_HVALUE+"=? AND "
																									+DB_COLUMN_DESCR+"=?";
	private Connection connection;
	
    /**
	 * Creates a MySQLHPatternDAO that uses the supplied Connection.
     * @param connection
     */
	public MySQLHPatternDAO(Connection connection) {
		super();
		this.connection = connection;
	}
	
	/* (non-Javadoc)
	 * @see de.rub.syssec.saaf.db.dao.GenericDAO#create(java.lang.Object)
	 */
	@Override
	public int create(HPatternInterface entity) throws DAOException, DuplicateEntityException {
		int id;
		int index=0;
		PreparedStatement insert;
		try {
			insert = connection.prepareStatement(QUERY_INSERT, Statement.RETURN_GENERATED_KEYS);
			if(entity.getPattern()!=null)
			{
				insert.setString(++index, entity.getPattern());
			}else{
				insert.setNull(++index, Types.VARCHAR);
			}
	
			if(entity.getSearchin()!=null)
			{
				insert.setString(++index, entity.getSearchin().toString().toUpperCase());
			}else{
				insert.setNull(++index, Types.VARCHAR);
			}
			
			//primitive type int cannot be null
			insert.setInt(++index, entity.getHvalue());
			
			if(entity.getDescription()!=null)
			{
				insert.setString(++index, entity.getDescription());
			}else{
				insert.setNull(++index, Types.VARCHAR);
			}
			insert.setBoolean(++index, entity.isActive());
			insert.executeUpdate();
			ResultSet rs = insert.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getInt(1);
			} else {
				throw new DAOException("Autogenerated keys could not be retrieved!");
			}
		} catch (SQLException e) {
			//use the SQL Error Code to throw specific exception for duplicate entries
			if(e.getSQLState().equalsIgnoreCase(SQL_ERROR_DUPLICATE) && e.getMessage().toLowerCase().contains("duplicate"))
			{
				throw new DuplicateEntityException("An entity with the same key attributes already exists",e);
			}else
			{
				throw new DAOException(e);
			}
		}
		return id;
	}

	/* (non-Javadoc)
	 * @see de.rub.syssec.saaf.db.dao.GenericDAO#read(int)
	 */
	@Override
	public HPatternInterface read(int id) throws DAOException {
		HPatternInterface result = null;
		String patternType =null;
		PreparedStatement selectStmt;
		try {
			selectStmt = connection.prepareStatement(QUERY_READ);
			selectStmt.setInt(1, id);
			ResultSet rs = selectStmt.executeQuery();
			if (rs.next()) {
				patternType = rs.getString(DB_COLUMN_SEARCHIN).toLowerCase();
				result = new HPattern(rs.getString(DB_COLUMN_PATTERN),
						PatternType.valueOf(PatternType.class,patternType.toUpperCase()),
						rs.getInt(DB_COLUMN_HVALUE),
						rs.getString(DB_COLUMN_DESCR));
				result.setId(rs.getInt(DB_COLUMN_ID));
				result.setActive(rs.getBoolean(DB_COLUMN_ACTIVE));
			}
		} catch (SQLException e) {
			throw new DAOException(e);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see de.rub.syssec.saaf.db.dao.GenericDAO#update(java.lang.Object)
	 */
	@Override
	public boolean update(HPatternInterface entity) throws DAOException, NoSuchEntityException {
		boolean success = false;
		int index=0;
		int recordsUpdated;
		PreparedStatement updateStmt;

		try {
			updateStmt = connection.prepareStatement(QUERY_UPDATE);
			if(entity.getPattern()!=null)
			{
				updateStmt.setString(++index, entity.getPattern());
			}else{
				updateStmt.setNull(++index, Types.VARCHAR);
			}

			if(entity.getSearchin()!=null)
			{
				updateStmt.setString(++index, entity.getSearchin().toString());
			}else{
				updateStmt.setNull(++index, Types.VARCHAR);
			}
			
			updateStmt.setInt(++index, entity.getHvalue());
			
			if(entity.getDescription()!=null)
			{
				updateStmt.setString(++index, entity.getDescription());
			}else{
				updateStmt.setNull(++index, Types.VARCHAR);
			}

			updateStmt.setBoolean(++index, entity.isActive());

			updateStmt.setInt(++index, entity.getId());
			recordsUpdated = updateStmt.executeUpdate();
			// this should affect at most one record
			if (recordsUpdated == 0){
				throw new NoSuchEntityException();
			}else if(recordsUpdated == 1) {
				success = true;
			} else {
				// the update affected multiple records this should not happen!
				throw new DAOException("Update of one HPattern affected multiple records. This should not happen!");
			}
		} catch (SQLException e) {
			throw new DAOException(e);
		}
		return success;
	}

	/* (non-Javadoc)
	 * @see de.rub.syssec.saaf.db.dao.GenericDAO#delete(java.lang.Object)
	 */
	@Override
	public boolean delete(HPatternInterface entity) throws DAOException, NoSuchEntityException {
		boolean success = false;
		int recordsAffected;
		try {
			PreparedStatement deleteStmt = connection.prepareStatement(QUERY_DELETE);
			deleteStmt.setInt(1, entity.getId());
			recordsAffected = deleteStmt.executeUpdate();
			// this should affect at most one record
			if(recordsAffected==0){
				throw new NoSuchEntityException();
			}else if (recordsAffected == 1) {
				success = true;
			} else if(recordsAffected >1) {
				throw new DAOException("Delete of one HPattern affected multiple records. This should not happen!");
			}

		} catch (SQLException e) {
			throw new DAOException(e);
		}
		return success;
	}

	@Override
	public List<HPatternInterface> readAll() throws DAOException {
		HPatternInterface result = null;
		String patternType =null;
		ArrayList<HPatternInterface> allPatterns = new ArrayList<HPatternInterface>();
		PreparedStatement selectStmt;
		try {
			selectStmt = connection.prepareStatement(QUERY_READ_ALL);
			ResultSet rs = selectStmt.executeQuery();
			while (rs.next()) {
				patternType = rs.getString(DB_COLUMN_SEARCHIN);
				result = new HPattern(rs.getString(DB_COLUMN_PATTERN),
						PatternType.valueOf(PatternType.class,patternType),
						rs.getInt(DB_COLUMN_HVALUE),
						rs.getString(DB_COLUMN_DESCR));
				result.setActive(rs.getBoolean(DB_COLUMN_ACTIVE));
				result.setId(rs.getInt(DB_COLUMN_ID));
				allPatterns.add(result);
			}
		} catch (SQLException e) {
			throw new DAOException(e);
		}
		return allPatterns;
	}

	@Override
	public int deleteAll() throws DAOException {
		int recordsAffected;
		try {
			PreparedStatement deleteStmt = connection.prepareStatement(DB_QUERY_DELETE_ALL);
			recordsAffected = deleteStmt.executeUpdate();
		} catch (SQLException e) {
			throw new DAOException(e);
		}
		return recordsAffected;
	}

	@Override
	public int findId(HPatternInterface candidate) throws DAOException {
		int id=0;
		PreparedStatement selectStmt;
		try {
			selectStmt = connection.prepareStatement(DB_QUERY_FIND_EXISITING);
			if(candidate.getPattern()!=null)
			{
				selectStmt.setString(1, candidate.getPattern());
			}else{
				selectStmt.setNull(1, Types.VARCHAR);
			}
			if(candidate.getSearchin()!=null)
			{
				selectStmt.setString(2, candidate.getSearchin().toString());
			}else{
				selectStmt.setNull(2, Types.VARCHAR);
			}

			selectStmt.setInt(3, candidate.getHvalue());
			
			if(candidate.getDescription()!=null)
			{
				selectStmt.setString(4, candidate.getDescription());
			}else{
				selectStmt.setNull(4, Types.VARCHAR);
			}
			
			ResultSet rs = selectStmt.executeQuery();
			if (rs.next()) {
				id = rs.getInt(DB_COLUMN_ID);
			}
		} catch (SQLException e) {
			throw new DAOException(e);
		}
		return id;
	}

	
}