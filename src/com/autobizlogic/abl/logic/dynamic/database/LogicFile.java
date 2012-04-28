package com.autobizlogic.abl.logic.dynamic.database;

import java.sql.Blob;
import java.sql.Timestamp;
import java.util.List;
import java.util.Vector;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

/**
 * The persistent entity used to store the blob for a jar of logic classes.
 * There is one instance of this class for each new version of a logic jar. The
 * effectiveDate attribute determines which one is in use.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */

@Entity
@Table(name="abl_logic_file")
public class LogicFile {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="ident", nullable=false)
	public long getIdent() { return ident; }
	public void setIdent(long ident) { this.ident = ident; }
	private long ident;

	@Column(name="name", nullable=false, length=300)
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	private String name;

	@Column(name="description", length=1000)
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	private String description;

	@Column(name="creation_date", nullable=false)
	public Timestamp getCreationDate() { return creationDate; }
	public void setCreationDate(Timestamp creationDate) { this.creationDate = creationDate; }
	private Timestamp creationDate;

	@Column(name="effective_date", nullable=false)
	public Timestamp getEffectiveDate() { return effectiveDate; }
	public void setEffectiveDate(Timestamp effectiveDate) { this.effectiveDate = effectiveDate; }
	private Timestamp effectiveDate;

	@Column(name="content", nullable=false)
	public Blob getContent() { return content; }
	public void setContent(Blob content) { this.content = content; }
	private Blob content;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="project_ident", nullable=false)
	public Project getProject() { return project; }
	public void setProject(Project project) { this.project = project; }
	private Project project;

	@OneToMany(cascade={CascadeType.ALL}, fetch=FetchType.LAZY, mappedBy="logicFile")
	@OrderBy("logDate")
	public List<LogicFileLog> getLogicFileLogs() { return this.logicFileLogs; }
	public void setLogicFileLogs(List<LogicFileLog> logicFileLogs) { this.logicFileLogs = logicFileLogs; }
	private List<LogicFileLog> logicFileLogs = new Vector<LogicFileLog>();
}

/*
 * The contents of this file are subject to the Automated Business Logic Commercial License Version 1.0 (the "License").
 * You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/sales/license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 