package com.autobizlogic.abl.logic.dynamic.database;

import java.util.List;
import java.util.Vector;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

/**
 * The persistent entity used to organize database-stored logic classes.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */

@Entity
@Table(name="abl_project")
public class Project {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="ident", nullable=false)
	public long getIdent() { return ident; }
	public void setIdent(long ident) { this.ident = ident; }
	private long ident;

	@Column(name="name", nullable=false, length=100, unique=true)
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	private String name;

	@Column(name="description", length=1000)
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	private String description;

	@OneToMany(cascade={CascadeType.ALL}, fetch=FetchType.LAZY, mappedBy="project")
	@OrderBy("effectiveDate desc")
	public List<LogicFile> getLogicFiles() { return this.logicFiles; }
	public void setLogicFiles(List<LogicFile> logicFiles) { this.logicFiles = logicFiles; }
	private List<LogicFile> logicFiles = new Vector<LogicFile>();
}

/*
 * The contents of this file are subject to the Automated Business Logic Commercial License Version 1.0 (the "License").
 * You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/sales/license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 